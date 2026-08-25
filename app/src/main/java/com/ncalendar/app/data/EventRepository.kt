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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val filter = CalendarFilter(prefs)

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

    private val refreshMutex = Mutex()
    private var lastRefreshAt = 0L
    private var lastRefreshDay: LocalDate? = null

    /**
     * Loads events/calendars for a wide window around today. Safe to call repeatedly — with
     * six call sites hitting a shared singleton (ViewModel init/resume, the ContentObserver on
     * ANY account's sync, widgets, ICS sync, boot), an unconditional query would be the
     * dominant cost in the app at even a few thousand instances. Passive/automatic callers
     * (the observer, periodic widget refreshes) leave [force] false and get skipped if a
     * refresh already landed within the last second and a half; anything the user is waiting
     * on directly (a save, a delete, a filter toggle, a subscription sync) MUST pass
     * force = true, or the change can appear to silently not take effect.
     */
    suspend fun refresh(today: LocalDate = LocalDate.now(), force: Boolean = false) {
        refreshMutex.withLock {
            val stale = today != lastRefreshDay || System.currentTimeMillis() - lastRefreshAt >= MIN_REFRESH_INTERVAL_MS
            if (!force && !stale) return@withLock
            val windowStart = today.minusMonths(6)
            val windowEnd = today.plusMonths(18)
            if (usingSystemCalendar) {
                registerObserver()
                val cals = provider.queryCalendars()
                _calendars.value = cals.ifEmpty { Calendars.all }
                val byId = _calendars.value.associateBy { it.id }
                val startMillis = windowStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMillis = windowEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                // Folded into the same query-level filter as calendar visibility, rather than a
                // separate in-memory pass — "Show holidays" off is just one more id subtracted
                // from the CALENDAR_ID IN (...) set, so it gets the same empty-set/covers-
                // everything fast paths CalendarProvider.queryInstances already has.
                val holidayIds = if (prefs.showHolidays) emptySet() else _calendars.value.filter { it.isHoliday }.mapTo(HashSet()) { it.id }
                val visibleIds = filter.visibleIds(_calendars.value) - holidayIds
                val calDefaults = byId.keys.associateWith { prefs.calendarDefaultReminders(it) }.filterValues { it.isNotEmpty() }
                _events.value = dedupe(
                    provider.queryInstances(startMillis, endMillis, byId, prefs.allEventReminders(), visibleIds, calDefaults),
                )
            } else {
                // Local-only (privacy opt-out) or no permission: events live in the
                // on-device Room store, never in the system provider.
                _calendars.value = Calendars.all
                val base = dao.observeAll().first().map { it.toDomain() }
                    .filter { filter.isCalendarEnabled(Calendars.get(it.calendarId)) }
                _events.value = base.flatMap { Recurrence.expand(it, windowStart, windowEnd) }
            }
            lastRefreshAt = System.currentTimeMillis()
            lastRefreshDay = today
        }
    }

    /**
     * Collapses the same real-world event appearing once per account.
     *
     * Anyone signed into two Google accounts gets the Indian/US/whatever holiday calendar
     * attached to BOTH, so every holiday arrived twice — and the same happens to a meeting
     * invited to a work and a personal address. Two entries with identical title and identical
     * start/end are indistinguishable to the reader, so showing both is never useful.
     *
     * Deliberately NOT keyed on calendar id: the whole point is to merge across calendars. The
     * first occurrence wins, which keeps the provider's own ordering (and so the calendar the
     * user's primary account sees) rather than picking arbitrarily.
     */
    private fun dedupe(events: List<EventItem>): List<EventItem> {
        val seen = HashSet<String>(events.size)
        return events.filter { seen.add("${it.title.trim().lowercase()}|${it.start}|${it.end}|${it.allDay}") }
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

    /**
     * The recurring SERIES' own start/duration for [instance] (not the instance's own date) —
     * what the editor must show when the user chose "all events" on a recurring event.
     * Everything else (title, location, calendar, reminders, ...) is shared across a
     * well-formed series, so [instance] itself is reused for those fields.
     */
    suspend fun seriesBase(instance: EventItem): EventItem? {
        val baseId = Recurrence.baseId(instance.id)
        return if (usingSystemCalendar) {
            val eventId = baseId.toLongOrNull() ?: return null
            val base = provider.queryEventBase(eventId) ?: return null
            instance.copy(id = baseId, start = base.start, end = base.start.plusMinutes(base.durationMinutes), allDay = base.allDay)
        } else {
            dao.getById(baseId)?.toDomain()
        }
    }

    /**
     * Create or update. [scope] only matters when [event] is an existing recurring instance
     * (its id carries the "::" instance suffix): ALL_EVENTS writes the whole series (the
     * default, and the only meaningful scope for a non-recurring or brand-new event);
     * THIS_EVENT splits just that one occurrence out via a CalendarContract exception (system
     * calendars) or a standalone replacement + base-series exception date (local-only).
     * Returns the (possibly new) saved id, or null if a THIS_EVENT split was refused by the
     * provider (e.g. a read-only calendar) — callers must NOT fall back to editing the whole
     * series on a null return, that's exactly the bug this scope split exists to prevent.
     */
    suspend fun save(event: EventItem, calendarId: String, scope: EditScope = EditScope.ALL_EVENTS): String? {
        val baseId = Recurrence.baseId(event.id)
        val isRecurringInstance = event.id.contains("::")
        if (!prefs.localOnly && provider.hasWritePermission() && calendarId.toLongOrNull() != null) {
            if (scope == EditScope.THIS_EVENT && isRecurringInstance) {
                val baseEventId = baseId.toLongOrNull() ?: return null
                val originalInstanceMillis = event.id.substringAfter("::").toLongOrNull() ?: return null
                val newId = provider.insertExceptionEdit(baseEventId, originalInstanceMillis, event, calendarId) ?: return null
                val idStr = newId.toString()
                prefs.setEventReminders(idStr, event.reminders)
                refresh(force = true)
                return idStr
            }
            val rrule = RRule.of(
                event.repeat, event.repeatInterval, event.repeatByDays,
                event.repeatEndDate, event.repeatEndCount, event.allDay,
            )
            val isNew = baseId.toLongOrNull() == null || baseId.startsWith("n")
            val savedId = if (isNew) {
                provider.insert(event, calendarId, rrule) ?: baseId
            } else {
                provider.update(baseId, event, calendarId, rrule); baseId
            }
            // Reminders live app-side so NCalendar is the only notifier for them.
            if (savedId.toLongOrNull() != null) prefs.setEventReminders(savedId, event.reminders)
            // Explicit refresh rather than waiting on the ContentObserver: callers that need to
            // resolve the saved id back to a fresh instance (see CalendarViewModel.saveEvent)
            // would otherwise race the async observer.
            refresh(force = true)
            return savedId
        }
        if (scope == EditScope.THIS_EVENT && isRecurringInstance) {
            val originalDate = Recurrence.instanceDate(event.id)
            if (originalDate != null) {
                dao.getById(baseId)?.let { base ->
                    dao.upsert(base.copy(repeatExceptionDates = base.repeatExceptionDates + originalDate))
                }
            }
            val newId = "n${System.currentTimeMillis()}"
            dao.upsert(
                event.copy(id = newId, calendarId = calendarId, repeat = RepeatRule.NONE, repeatExceptionDates = emptySet(), isRecurring = false)
                    .toEntity()
            )
            refresh(force = true)
            return newId
        }
        dao.upsert(event.copy(id = baseId, calendarId = calendarId).toEntity())
        refresh(force = true)
        return baseId
    }

    /**
     * Sets app-managed reminders for [id] WITHOUT touching the event itself — the path for
     * setting a reminder on an event from a read-only calendar (a synced Birthdays or Holidays
     * calendar, most commonly), where [save]'s normal write path isn't available because the
     * provider will just silently reject the update.
     */
    suspend fun setReminders(id: String, minutes: List<Int>) {
        val baseId = Recurrence.baseId(id)
        prefs.setEventReminders(baseId, minutes)
        if (prefs.syncRemindersToProvider) {
            baseId.toLongOrNull()?.let { provider.writeReminders(it, minutes) }
        }
        refresh(force = true)
    }

    /**
     * Applies (or undoes) provider-side reminder mirroring across every event that already has
     * app-side reminders. Without this, flipping the Settings toggle would only affect events
     * edited afterwards, which reads as the setting silently not working.
     */
    suspend fun applyProviderReminderSync(enabled: Boolean) {
        if (!provider.hasWritePermission()) return
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            prefs.allEventReminders().forEach { (eventId, minutes) ->
                val id = eventId.toLongOrNull() ?: return@forEach
                if (enabled) provider.writeReminders(id, minutes) else provider.clearProviderReminders(id)
            }
        }
        refresh(force = true)
    }

    suspend fun delete(id: String, scope: EditScope = EditScope.ALL_EVENTS) {
        val baseId = Recurrence.baseId(id)
        val isRecurringInstance = id.contains("::")
        if (!prefs.localOnly && provider.hasWritePermission() && baseId.toLongOrNull() != null) {
            if (scope == EditScope.THIS_EVENT && isRecurringInstance) {
                val baseEventId = baseId.toLongOrNull() ?: return
                val originalInstanceMillis = id.substringAfter("::").toLongOrNull() ?: return
                provider.insertExceptionCancel(baseEventId, originalInstanceMillis)
                refresh(force = true)
                return
            }
            provider.delete(baseId)
            prefs.clearEventReminders(baseId)
            refresh(force = true)
        } else {
            if (scope == EditScope.THIS_EVENT && isRecurringInstance) {
                val originalDate = Recurrence.instanceDate(id)
                if (originalDate != null) {
                    dao.getById(baseId)?.let { base ->
                        dao.upsert(base.copy(repeatExceptionDates = base.repeatExceptionDates + originalDate))
                    }
                }
                refresh(force = true)
                return
            }
            dao.deleteById(baseId)
            refresh(force = true)
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
        refresh(force = true)
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
        private const val MIN_REFRESH_INTERVAL_MS = 1_500L

        @Volatile private var instance: EventRepository? = null
        fun get(context: Context): EventRepository = instance ?: synchronized(this) {
            instance ?: EventRepository(context, NCalendarDatabase.get(context).eventDao()).also { instance = it }
        }
    }
}
