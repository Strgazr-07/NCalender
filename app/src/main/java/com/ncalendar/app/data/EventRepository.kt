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
        if (!usingSystemCalendar || !provider.hasWritePermission()) return 0
        val text = kotlinx.coroutines.withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        }
        val values = IcsSyncManager.parseEvents(text)
        val imported = values.count { provider.insertRaw(calendarId, it) }
        refresh()
        return imported
    }

    companion object {
        @Volatile private var instance: EventRepository? = null
        fun get(context: Context): EventRepository = instance ?: synchronized(this) {
            instance ?: EventRepository(context, NCalendarDatabase.get(context).eventDao()).also { instance = it }
        }
    }
}
