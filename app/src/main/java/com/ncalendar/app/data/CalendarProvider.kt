package com.ncalendar.app.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Reads and writes the device's real calendars via CalendarContract. Instances are
 * recurrence-expanded by the provider, so repeating system events "just work".
 */
class CalendarProvider(private val context: Context) {

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun queryCalendars(): List<CalendarInfo> {
        if (!hasReadPermission()) return emptyList()
        val out = ArrayList<CalendarInfo>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0).toString()
                    val name = c.getString(1) ?: "Calendar"
                    val account = c.getString(2) ?: ""
                    val accountType = c.getString(3) ?: ""
                    val color = c.getInt(4)
                    val access = c.getInt(5)
                    val isPrimary = c.getInt(6) == 1
                    out.add(
                        CalendarInfo(
                            id = id,
                            name = name,
                            color = if (color != 0) Color(color) else Color(0xFF8A8A88),
                            accountName = account,
                            accountType = accountType,
                            isWritable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            isPrimary = isPrimary,
                        )
                    )
                }
            }
        }
        return out
    }

    fun queryInstances(
        windowStartMillis: Long,
        windowEndMillis: Long,
        calendarsById: Map<String, CalendarInfo>,
        localReminders: Map<String, List<Int>> = emptyMap(),
    ): List<EventItem> {
        if (!hasReadPermission()) return emptyList()
        val out = ArrayList<EventItem>()
        val providerReminders = queryReminderMap()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, windowStartMillis)
        ContentUris.appendId(builder, windowEndMillis)
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.DESCRIPTION,
        )
        val zone = ZoneId.systemDefault()
        runCatching {
            context.contentResolver.query(builder.build(), proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val eventId = c.getLong(0)
                    val begin = c.getLong(1)
                    val end = c.getLong(2)
                    val title = c.getString(3) ?: "(No title)"
                    val location = c.getString(4)
                    val allDay = c.getInt(5) == 1
                    val calId = c.getLong(6).toString()
                    val rrule = c.getString(7)
                    val desc = c.getString(8)
                    val cal = calendarsById[calId]
                    val repeat = RepeatRule.parseRRule(rrule)

                    val startLdt = if (allDay) {
                        Instant.ofEpochMilli(begin).atZone(ZoneId.of("UTC")).toLocalDate().atStartOfDay()
                    } else {
                        Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
                    }
                    val endLdt = if (allDay) {
                        // The provider stores an all-day END exclusively: UTC midnight of
                        // the day AFTER the last day. Pull it back to the inclusive last
                        // day so a single-day event doesn't bleed onto the next day.
                        val lastDay = Instant.ofEpochMilli(end).atZone(ZoneId.of("UTC")).toLocalDate().minusDays(1)
                        maxOf(startLdt.toLocalDate(), lastDay).atStartOfDay()
                    } else {
                        Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime()
                    }
                    val recurring = !rrule.isNullOrBlank()
                    val id = if (recurring) "$eventId::$begin" else eventId.toString()
                    // App-managed reminders win; fall back to reminders set in other
                    // apps so they still show and fire here.
                    val reminders = localReminders[eventId.toString()]
                        ?: providerReminders[eventId].orEmpty()
                    out.add(
                        EventItem(
                            id = id,
                            title = title,
                            calendarId = calId,
                            start = startLdt,
                            end = endLdt,
                            allDay = allDay,
                            repeat = repeat.rule,
                            repeatInterval = repeat.interval,
                            repeatByDays = repeat.byDays,
                            repeatEndDate = repeat.until,
                            repeatEndCount = repeat.count,
                            reminders = reminders,
                            location = location,
                            notes = desc,
                            color = cal?.color ?: Color(0xFF8A8A88),
                            calendarName = cal?.name ?: "Calendar",
                            isRecurring = recurring,
                        )
                    )
                }
            }
        }
        return out
    }

    /** Inserts a new event; returns the new event id as a string, or null on failure. */
    fun insert(event: EventItem, calendarId: String, rrule: String?): String? {
        if (!hasWritePermission()) return null
        val tz = TimeZone.getDefault().id
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId.toLongOrNull() ?: return null)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            // All-day events MUST be stored at UTC midnight per the CalendarContract
            // contract; timed events use the device zone. Writing device-zone millis
            // for an all-day event shifts it a day in any non-UTC timezone.
            if (event.allDay) {
                put(CalendarContract.Events.DTSTART, event.start.toUtcMidnightMillis())
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, event.start.toMillis())
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            }
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, durationString(event))
            } else {
                put(CalendarContract.Events.DTEND, endMillis(event))
                if (event.allDay) put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
            }
            event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            event.notes?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }
        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull()
            ?: return null
        // Reminders deliberately NOT written to the provider: NCalendar schedules
        // its own alarms, and provider alerts would make Google Calendar notify too.
        return ContentUris.parseId(uri).toString()
    }

    fun update(baseId: String, event: EventItem, calendarId: String, rrule: String?): Boolean {
        if (!hasWritePermission()) return false
        val id = baseId.toLongOrNull() ?: return false
        val tz = TimeZone.getDefault().id
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.CALENDAR_ID, calendarId.toLongOrNull() ?: return false)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            // All-day writes use UTC midnight; timed writes use the device zone.
            // EVENT_END_TIMEZONE is set explicitly so toggling all-day on an
            // existing event doesn't leave a stale end zone behind.
            if (event.allDay) {
                put(CalendarContract.Events.DTSTART, event.start.toUtcMidnightMillis())
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, event.start.toMillis())
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            }
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, durationString(event))
                putNull(CalendarContract.Events.DTEND)
                putNull(CalendarContract.Events.EVENT_END_TIMEZONE)
            } else {
                put(CalendarContract.Events.DTEND, endMillis(event))
                put(CalendarContract.Events.EVENT_END_TIMEZONE, if (event.allDay) "UTC" else tz)
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
            }
            put(CalendarContract.Events.EVENT_LOCATION, event.location ?: "")
            put(CalendarContract.Events.DESCRIPTION, event.notes ?: "")
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        val rows = runCatching { context.contentResolver.update(uri, values, null, null) }.getOrDefault(0)
        if (rows > 0) {
            // Clear provider-side alerts so only NCalendar notifies for this event.
            deleteReminders(id)
        }
        return rows > 0
    }

    fun delete(baseId: String): Boolean {
        if (!hasWritePermission()) return false
        val id = baseId.toLongOrNull() ?: return false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0) > 0
    }

    fun insertRaw(calendarId: String, values: ContentValues): Boolean {
        if (!hasWritePermission()) return false
        val id = calendarId.toLongOrNull() ?: return false
        val v = ContentValues(values).apply { put(CalendarContract.Events.CALENDAR_ID, id) }
        return runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) }.getOrNull() != null
    }

    /** All provider reminder rows, grouped by event id (one query for the whole window). */
    private fun queryReminderMap(): Map<Long, List<Int>> {
        val map = HashMap<Long, MutableList<Int>>()
        val proj = arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES)
        runCatching {
            context.contentResolver.query(CalendarContract.Reminders.CONTENT_URI, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    map.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getInt(1))
                }
            }
        }
        return map
    }

    private fun deleteReminders(eventId: Long) {
        runCatching {
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
            )
        }
    }

    private fun durationString(event: EventItem): String =
        if (event.allDay) {
            // All-day durations are whole days (end date is inclusive here).
            val days = java.time.temporal.ChronoUnit.DAYS
                .between(event.start.toLocalDate(), event.end.toLocalDate()) + 1
            "P${days.coerceAtLeast(1)}D"
        } else {
            val minutes = java.time.temporal.ChronoUnit.MINUTES.between(event.start, event.end).coerceAtLeast(0)
            "PT${minutes}M"
        }

    private fun LocalDateTime.toMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** All-day DTSTART/DTEND must be expressed at UTC midnight per CalendarContract. */
    private fun LocalDateTime.toUtcMidnightMillis(): Long =
        toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    /**
     * DTEND in millis. All-day END is exclusive — UTC midnight of the day AFTER the
     * inclusive last day; timed events keep their device-zone end.
     */
    private fun endMillis(event: EventItem): Long =
        if (event.allDay)
            event.end.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        else
            event.end.toMillis()
}
