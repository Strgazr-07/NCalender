package com.ncalendar.app.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.net.Uri
import com.ncalendar.app.data.ics.IcsSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single facade over two backends:
 *  - **System** (CalendarContract) when calendar permission is granted — the user's real events.
 *  - **Demo** (Room) otherwise — the seeded showcase data, so the app is never empty.
 *
 * Recurrence is expanded either way (provider Instances for system; [Recurrence] for demo).
 */
class EventRepository private constructor(
    context: Context,
    private val dao: EventDao,
) {
    private val appContext = context.applicationContext
    private val provider = CalendarProvider(appContext)
    private val prefs = Prefs(appContext)

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(Calendars.all)
    val calendars: StateFlow<List<CalendarInfo>> = _calendars

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events

    /** True when the app is reading real device calendars (permission granted AND the user hasn't opted for local-only). */
    val usingSystemCalendar: Boolean get() = provider.hasReadPermission() && !prefs.localOnly

    private var observerRegistered = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshAsync() }
    }

    /** Loads events/calendars for a wide window around today. Safe to call repeatedly. */
    suspend fun refresh(today: LocalDate = LocalDate.now()) {
        val windowStart = today.minusMonths(6)
        val windowEnd = today.plusMonths(18)
        if (usingSystemCalendar) {
            registerObserver()
            val cals = provider.queryCalendars()
            _calendars.value = cals.ifEmpty { Calendars.all }
            val byId = _calendars.value.associateBy { it.id }
            val startMillis = windowStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = windowEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            _events.value = provider.queryInstances(startMillis, endMillis, byId, prefs.allEventReminders())
        } else {
            // Local-only (privacy opt-out) or no permission: events live in the
            // on-device Room store, never in the system provider.
            _calendars.value = Calendars.all
            val base = dao.observeAll().first().map { it.toDomain() }
            _events.value = base.flatMap { Recurrence.expand(it, windowStart, windowEnd) }
        }
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private fun refreshAsync() {
        ioScope.launch { runCatching { refresh() } }
    }

    private fun registerObserver() {
        if (observerRegistered) return
        runCatching {
            appContext.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
            observerRegistered = true
        }
    }

    val writableCalendars: List<CalendarInfo> get() = _calendars.value.filter { it.isWritable }

    /** Create or update. Returns the (possibly new) base id. */
    suspend fun save(event: EventItem, calendarId: String): String {
        val baseId = Recurrence.baseId(event.id)
        if (!prefs.localOnly && provider.hasWritePermission() && calendarId.toLongOrNull() != null) {
            val rrule = event.repeat.toRRule(event.repeatInterval, event.repeatByDays, event.repeatEndDate, event.repeatEndCount)
            val isNew = baseId.toLongOrNull() == null || baseId.startsWith("n")
            val savedId = if (isNew) {
                provider.insert(event, calendarId, rrule) ?: baseId
            } else {
                provider.update(baseId, event, calendarId, rrule); baseId
            }
            // Reminders live app-side so NCalendar is the only notifier for them.
            if (savedId.toLongOrNull() != null) prefs.setEventReminders(savedId, event.reminders)
            return savedId
        }
        dao.upsert(event.copy(id = baseId, calendarId = calendarId).toEntity())
        refresh()
        return baseId
    }

    suspend fun delete(id: String) {
        val baseId = Recurrence.baseId(id)
        if (!prefs.localOnly && provider.hasWritePermission() && baseId.toLongOrNull() != null) {
            provider.delete(baseId)
            prefs.clearEventReminders(baseId)
            refresh()
        } else {
            dao.deleteById(baseId)
            refresh()
        }
    }

    suspend fun importIcs(uri: Uri, calendarId: String): Int {
        val text = kotlinx.coroutines.withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        }
        val values = IcsSyncManager.parseEvents(text)
        val imported = if (usingSystemCalendar && provider.hasWritePermission() && calendarId.toLongOrNull() != null) {
            values.count { provider.insertRaw(calendarId, it) }
        } else {
            val targetCalendar = calendarId.takeIf { it.isNotBlank() } ?: Calendars.PERSONAL.id
            val events = values.mapIndexedNotNull { index, v -> rawIcsToLocalEvent(v, index, targetCalendar) }
            events.forEach { dao.upsert(it.toEntity()) }
            events.size
        }
        refresh()
        return imported
    }

    private fun rawIcsToLocalEvent(values: android.content.ContentValues, index: Int, calendarId: String): EventItem? {
        val title = values.getAsString(CalendarContract.Events.TITLE)?.takeIf { it.isNotBlank() } ?: "(No title)"
        val allDay = values.getAsInteger(CalendarContract.Events.ALL_DAY) == 1
        val startMillis = values.getAsLong(CalendarContract.Events.DTSTART) ?: return null
        val rrule = values.getAsString(CalendarContract.Events.RRULE)
        val repeat = RepeatRule.parseRRule(rrule)
        val zone = ZoneId.systemDefault()
        val start = if (allDay) {
            Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate().atStartOfDay()
        } else {
            Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDateTime()
        }
        val end = if (allDay) {
            val endMillis = values.getAsLong(CalendarContract.Events.DTEND)
            val lastDay = if (endMillis != null) {
                Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate().minusDays(1)
            } else {
                start.toLocalDate().plusDays(parseDurationDays(values.getAsString(CalendarContract.Events.DURATION)).toLong() - 1)
            }
            maxOf(start.toLocalDate(), lastDay).atStartOfDay()
        } else {
            val endMillis = values.getAsLong(CalendarContract.Events.DTEND)
            if (endMillis != null) {
                Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDateTime()
            } else {
                start.plusSeconds(parseDurationSeconds(values.getAsString(CalendarContract.Events.DURATION)).coerceAtLeast(3600))
            }
        }
        return EventItem(
            id = "ics${System.currentTimeMillis()}_$index",
            title = title,
            calendarId = calendarId.takeIf { it.isNotBlank() } ?: Calendars.PERSONAL.id,
            start = start,
            end = end,
            allDay = allDay,
            repeat = repeat.rule,
            repeatInterval = repeat.interval,
            repeatByDays = repeat.byDays,
            repeatEndDate = repeat.until,
            repeatEndCount = repeat.count,
            location = values.getAsString(CalendarContract.Events.EVENT_LOCATION)?.takeIf { it.isNotBlank() },
            notes = values.getAsString(CalendarContract.Events.DESCRIPTION)?.takeIf { it.isNotBlank() },
            color = Calendars.get(calendarId).color,
            calendarName = Calendars.get(calendarId).name,
            isRecurring = rrule != null,
        )
    }

    private fun parseDurationDays(duration: String?): Int {
        if (duration.isNullOrBlank()) return 1
        val match = Regex("P(\\d+)D").find(duration)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }

    private fun parseDurationSeconds(duration: String?): Long {
        if (duration.isNullOrBlank()) return 3600
        Regex("PT(\\d+)S").find(duration)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it }
        Regex("PT(\\d+)M").find(duration)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it * 60 }
        Regex("PT(\\d+)H").find(duration)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it * 3600 }
        return 3600
    }

    companion object {
        @Volatile private var instance: EventRepository? = null
        fun get(context: Context): EventRepository = instance ?: synchronized(this) {
            instance ?: EventRepository(context, NCalendarDatabase.get(context).eventDao()).also { instance = it }
        }
    }
}
