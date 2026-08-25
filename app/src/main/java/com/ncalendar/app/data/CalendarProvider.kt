package com.ncalendar.app.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
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
            CalendarContract.Calendars.OWNER_ACCOUNT,
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
                    val owner = c.getString(7) ?: ""
                    out.add(
                        CalendarInfo(
                            id = id,
                            name = name,
                            color = if (color != 0) Color(color) else Color(0xFF8A8A88),
                            accountName = account,
                            accountType = accountType,
                            isWritable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            isPrimary = isPrimary,
                            isHoliday = isHolidayCalendar(owner, name),
                        )
                    )
                }
            }
        }
        return out
    }

    /**
     * Android's CalendarContract has no explicit "this is a holiday calendar" field, so this
     * uses the two heuristics calendar apps commonly rely on: Google's regional public holiday
     * calendars are owned by an account ending "#holiday@group.v.calendar.google.com" (e.g.
     * "en.indian#holiday@group.v.calendar.google.com"), and other providers (Outlook, etc.)
     * typically just name the calendar "Holidays in <country>". Neither is authoritative on
     * its own, so both are checked.
     */
    private fun isHolidayCalendar(ownerAccount: String, displayName: String): Boolean =
        ownerAccount.contains("holiday@group.v.calendar.google.com", ignoreCase = true) ||
            displayName.contains("holidays in", ignoreCase = true)

    private data class RawInstance(
        val eventId: Long, val begin: Long, val end: Long, val title: String,
        val location: String?, val allDay: Boolean, val calId: String,
        val rrule: String?, val desc: String?,
    )

    /**
     * [visibleCalendarIds]: null means "don't filter" (used when the caller already knows
     * every known calendar is visible); an empty set short-circuits to no query at all, since
     * "CALENDAR_ID IN ()" is invalid SQL — never build that selection string.
     */
    fun queryInstances(
        windowStartMillis: Long,
        windowEndMillis: Long,
        calendarsById: Map<String, CalendarInfo>,
        localReminders: Map<String, List<Int>> = emptyMap(),
        visibleCalendarIds: Set<String>? = null,
        calendarDefaultReminders: Map<String, List<Int>> = emptyMap(),
    ): List<EventItem> {
        if (!hasReadPermission()) return emptyList()
        if (visibleCalendarIds != null && visibleCalendarIds.isEmpty()) return emptyList()
        val raw = ArrayList<RawInstance>()
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
        // Only filter when it's a real subset of what's known — an unnecessary IN clause is
        // pure overhead, and lets the provider use its normal fast path in the common case.
        val filtering = visibleCalendarIds != null && visibleCalendarIds.size < calendarsById.size
        val selection = if (filtering) "${CalendarContract.Instances.CALENDAR_ID} IN (${visibleCalendarIds.joinToString(",") { "?" }})" else null
        val selectionArgs = if (filtering) visibleCalendarIds.toTypedArray() else null
        runCatching {
            context.contentResolver.query(builder.build(), proj, selection, selectionArgs, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    raw.add(
                        RawInstance(
                            eventId = c.getLong(0), begin = c.getLong(1), end = c.getLong(2),
                            title = c.getString(3) ?: "(No title)", location = c.getString(4),
                            allDay = c.getInt(5) == 1, calId = c.getLong(6).toString(),
                            rrule = c.getString(7), desc = c.getString(8),
                        )
                    )
                }
            }
        }
        if (raw.isEmpty()) return emptyList()
        // Scoped to just the event ids actually in this window, instead of the whole
        // Reminders table — that was a full-table scan on every refresh, and refresh fires on
        // any account's sync, not just this app's own writes.
        val providerReminders = queryReminderMap(raw.mapTo(HashSet()) { it.eventId })
        val zone = ZoneId.systemDefault()
        val out = ArrayList<EventItem>(raw.size)
        for (r in raw) {
            val cal = calendarsById[r.calId]
            val repeat = RepeatRule.parseRRule(r.rrule)
            val startLdt = if (r.allDay) {
                CalendarTimes.allDayStartDate(r.begin).atStartOfDay()
            } else {
                CalendarTimes.timedDateTime(r.begin, zone)
            }
            val endLdt = if (r.allDay) {
                // The provider stores an all-day END exclusively: UTC midnight of the day
                // AFTER the last day. Pull it back to the inclusive last day so a single-day
                // event doesn't bleed onto the next day.
                CalendarTimes.allDayEndDate(startLdt.toLocalDate(), r.end).atStartOfDay()
            } else {
                CalendarTimes.timedDateTime(r.end, zone)
            }
            val recurring = !r.rrule.isNullOrBlank()
            val id = if (recurring) "${r.eventId}::${r.begin}" else r.eventId.toString()
            // App-managed reminders win; fall back to reminders set in other apps so they
            // still show and fire here; and if the event has genuinely none of its own (e.g.
            // a read-only synced Birthdays/Holidays calendar, which never carries any
            // CalendarContract.Reminders rows and can't be edited to add one), fall back to
            // this calendar's user-configured default.
            val reminders = localReminders[r.eventId.toString()]
                ?: providerReminders[r.eventId]?.takeIf { it.isNotEmpty() }
                ?: calendarDefaultReminders[r.calId].orEmpty()
            out.add(
                EventItem(
                    id = id,
                    title = r.title,
                    calendarId = r.calId,
                    start = startLdt,
                    end = endLdt,
                    allDay = r.allDay,
                    repeat = repeat.rule,
                    repeatInterval = repeat.interval,
                    repeatByDays = repeat.byDays,
                    repeatEndDate = repeat.until,
                    repeatEndCount = repeat.count,
                    reminders = reminders,
                    location = r.location,
                    notes = r.desc,
                    color = cal?.color ?: Color(0xFF8A8A88),
                    calendarName = cal?.name ?: "Calendar",
                    isRecurring = recurring,
                )
            )
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
                put(CalendarContract.Events.DTSTART, CalendarTimes.toUtcMidnightMillis(event.start.toLocalDate()))
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, CalendarTimes.toMillis(event.start))
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            }
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, CalendarTimes.durationString(event))
            } else {
                put(CalendarContract.Events.DTEND, CalendarTimes.endMillis(event))
                if (event.allDay) put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
            }
            event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            event.notes?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }
        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull()
            ?: return null
        val newId = ContentUris.parseId(uri)
        // Reminders are deliberately NOT written to the provider by default: NCalendar
        // schedules its own alarms, and provider alerts would make Google Calendar notify for
        // the same event too. The Settings toggle opts into that duplication on purpose.
        if (Prefs(context).syncRemindersToProvider) writeReminders(newId, event.reminders)
        return newId.toString()
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
                put(CalendarContract.Events.DTSTART, CalendarTimes.toUtcMidnightMillis(event.start.toLocalDate()))
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, CalendarTimes.toMillis(event.start))
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
            }
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, CalendarTimes.durationString(event))
                putNull(CalendarContract.Events.DTEND)
                putNull(CalendarContract.Events.EVENT_END_TIMEZONE)
            } else {
                put(CalendarContract.Events.DTEND, CalendarTimes.endMillis(event))
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
            // By default, clear provider-side alerts so only NCalendar notifies for this
            // event. The opt-in Settings toggle inverts that for users who explicitly WANT
            // Google Calendar to notify them as well.
            if (Prefs(context).syncRemindersToProvider) {
                writeReminders(id, event.reminders)
            } else {
                deleteReminders(id)
            }
        }
        return rows > 0
    }

    /** Mirrors app-side reminders into CalendarContract. Only called when the user has opted
     *  in — see [Prefs.syncRemindersToProvider]. */
    fun writeReminders(eventId: Long, minutes: List<Int>) {
        if (!hasWritePermission()) return
        deleteReminders(eventId)
        // Calendars advertise a MAX_REMINDERS (commonly 5); more than a handful is beyond
        // what any provider will accept anyway.
        minutes.distinct().take(5).forEach { m ->
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, m)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values) }
        }
    }

    fun clearProviderReminders(eventId: Long) = deleteReminders(eventId)

    fun delete(baseId: String): Boolean {
        if (!hasWritePermission()) return false
        val id = baseId.toLongOrNull() ?: return false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0) > 0
    }

    /**
     * The recurring SERIES' own DTSTART/duration — not any one instance's. Editing "all events"
     * in a series must write this back as DTSTART; writing an instance's own (possibly
     * mid-series) date there would move the whole series (see EventRepository.save).
     */
    fun queryEventBase(eventId: Long): EventBase? {
        if (!hasReadPermission()) return null
        val proj = arrayOf(
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
        )
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return runCatching {
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val dtStartMillis = c.getLong(0)
                val dtEndMillis = if (c.isNull(1)) null else c.getLong(1)
                val duration = c.getString(2)
                val allDay = c.getInt(3) == 1
                val rrule = c.getString(4)
                val start = if (allDay) CalendarTimes.allDayStartDate(dtStartMillis).atStartOfDay()
                    else CalendarTimes.timedDateTime(dtStartMillis)
                val minutes = when {
                    dtEndMillis != null && allDay -> {
                        val lastDay = CalendarTimes.allDayEndDate(start.toLocalDate(), dtEndMillis)
                        (java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), lastDay) + 1) * 24 * 60
                    }
                    dtEndMillis != null -> (dtEndMillis - dtStartMillis) / 60_000
                    else -> CalendarTimes.parseDurationMinutes(duration, allDay)
                }
                EventBase(start, minutes, allDay, rrule)
            }
        }.getOrNull()
    }

    /**
     * Splits ONE occurrence out of a recurring series so only it is edited, leaving the rest of
     * the series (and its RRULE) untouched. [originalInstanceTimeMillis] must be the exact
     * instance BEGIN the provider reported for that occurrence — already embedded in its
     * instance id ("eventId::begin") — recomputing it from the edited event's own (possibly
     * changed) start would target the wrong occurrence. Returns the new exception row's own
     * event id, or null if the provider refused (e.g. a read-only calendar) — callers must not
     * fall back to editing the whole series on failure.
     */
    fun insertExceptionEdit(baseEventId: Long, originalInstanceTimeMillis: Long, event: EventItem, calendarId: String): Long? {
        if (!hasWritePermission()) return null
        val id = calendarId.toLongOrNull() ?: return null
        val exceptionUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, baseEventId)
        val tz = TimeZone.getDefault().id
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, originalInstanceTimeMillis)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.CALENDAR_ID, id)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            if (event.allDay) {
                put(CalendarContract.Events.DTSTART, CalendarTimes.toUtcMidnightMillis(event.start.toLocalDate()))
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.DTEND, CalendarTimes.endMillis(event))
                put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, CalendarTimes.toMillis(event.start))
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
                put(CalendarContract.Events.DTEND, CalendarTimes.endMillis(event))
                put(CalendarContract.Events.EVENT_END_TIMEZONE, tz)
            }
            put(CalendarContract.Events.EVENT_LOCATION, event.location ?: "")
            put(CalendarContract.Events.DESCRIPTION, event.notes ?: "")
        }
        val uri = runCatching { context.contentResolver.insert(exceptionUri, values) }.getOrNull() ?: return null
        return runCatching { ContentUris.parseId(uri) }.getOrNull()
    }

    /** Cancels ONE occurrence of a recurring series, leaving the series and every other
     *  occurrence untouched. Same ORIGINAL_INSTANCE_TIME caveat as [insertExceptionEdit]. */
    fun insertExceptionCancel(baseEventId: Long, originalInstanceTimeMillis: Long): Boolean {
        if (!hasWritePermission()) return false
        val exceptionUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, baseEventId)
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, originalInstanceTimeMillis)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        val uri = runCatching { context.contentResolver.insert(exceptionUri, values) }.getOrNull()
        return uri != null
    }

    fun insertRaw(calendarId: String, values: ContentValues): Boolean {
        if (!hasWritePermission()) return false
        val id = calendarId.toLongOrNull() ?: return false
        val v = ContentValues(values).apply { put(CalendarContract.Events.CALENDAR_ID, id) }
        return runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) }.getOrNull() != null
    }

    /** Provider reminder rows for just [eventIds], grouped by event id. Scoped rather than a
     *  full-table scan — this runs on every refresh, and refresh fires on any account's sync,
     *  not just this app's own writes. */
    private fun queryReminderMap(eventIds: Set<Long>): Map<Long, List<Int>> {
        if (eventIds.isEmpty()) return emptyMap()
        val map = HashMap<Long, MutableList<Int>>()
        val proj = arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES)
        // Chunked to stay under SQLite's historical 999-bound-variable limit.
        eventIds.chunked(900).forEach { chunk ->
            val selection = "${CalendarContract.Reminders.EVENT_ID} IN (${chunk.joinToString(",") { "?" }})"
            val args = chunk.map { it.toString() }.toTypedArray()
            runCatching {
                context.contentResolver.query(CalendarContract.Reminders.CONTENT_URI, proj, selection, args, null)?.use { c ->
                    while (c.moveToNext()) {
                        map.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getInt(1))
                    }
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

}
