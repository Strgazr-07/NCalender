package com.ncalendar.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ncalendar.app.data.CalendarInfo
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.EventRepository
import com.ncalendar.app.data.Prefs
import com.ncalendar.app.data.RepeatRule
import com.ncalendar.app.data.ics.IcsSubscription
import com.ncalendar.app.data.ics.IcsSyncManager
import com.ncalendar.app.data.ics.IcsSyncWorker
import com.ncalendar.app.data.ics.SubscriptionCalendars
import com.ncalendar.app.notifications.ReminderScheduler
import com.ncalendar.app.widget.AppWidgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = EventRepository.get(app)
    private val prefs = Prefs(app)
    private val appCtx: Context = app.applicationContext

    val today: LocalDate = LocalDate.now()

    var state by mutableStateOf(
        AppState(anchor = today, selDay = today, pickerYear = today.year)
    )
        private set

    val events: StateFlow<List<EventItem>> = repo.events
    val calendars: StateFlow<List<CalendarInfo>> = repo.calendars

    val usingSystemCalendar: Boolean get() = repo.usingSystemCalendar

    private val _now = MutableStateFlow(LocalDateTime.now())
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    var ndot by mutableStateOf(prefs.ndotNumerals)
        private set
    var weekStart by mutableStateOf(prefs.weekStart)
        private set
    var defaultReminder by mutableStateOf(prefs.defaultReminderMinutes)
        private set
    var darkTheme by mutableStateOf(prefs.darkTheme)
        private set

    /** Privacy opt-out: never read/write the system (Google) calendars; Room only. */
    var localOnly by mutableStateOf(prefs.localOnly)
        private set

    fun updateLocalOnly(v: Boolean) {
        prefs.localOnly = v
        localOnly = v
        viewModelScope.launch { repo.refresh(today) }
    }

    // ---------------- .ics subscriptions ----------------

    /** The subscribed-feed registry (mirrored events live in the system calendar). */
    var subscriptions by mutableStateOf(prefs.icsSubscriptions)
        private set
    var syncingSubs by mutableStateOf(false)
        private set

    /** Subscriptions need real calendar access — they mirror into a local calendar. */
    val canSubscribe: Boolean get() = repo.usingSystemCalendar

    fun addSubscription(rawUrl: String, name: String, colorArgb: Int) {
        val url = IcsSubscription.normalizeUrl(rawUrl)
        val sub = IcsSubscription(url = url, name = name.ifBlank { hostOf(url) }, colorArgb = colorArgb)
        subscriptions = (prefs.icsSubscriptions + sub).also { prefs.icsSubscriptions = it }
        IcsSyncWorker.schedulePeriodic(appCtx)
        viewModelScope.launch {
            syncingSubs = true
            subscriptions = IcsSyncManager.syncOne(appCtx, sub.id)
            repo.refresh(today)
            AppWidgets.refreshAll(appCtx)
            syncingSubs = false
        }
    }

    fun refreshSubscriptions() {
        if (subscriptions.isEmpty() || syncingSubs) return
        viewModelScope.launch {
            syncingSubs = true
            subscriptions = IcsSyncManager.syncAll(appCtx)
            repo.refresh(today)
            AppWidgets.refreshAll(appCtx)
            syncingSubs = false
        }
    }

    fun removeSubscription(id: String) {
        val sub = prefs.icsSubscriptions.find { it.id == id }
        subscriptions = prefs.icsSubscriptions.filterNot { it.id == id }.also { prefs.icsSubscriptions = it }
        if (subscriptions.isEmpty()) IcsSyncWorker.cancelPeriodic(appCtx)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            sub?.calendarId?.let { SubscriptionCalendars.deleteCalendar(appCtx, it) }
            repo.refresh(today)
            AppWidgets.refreshAll(appCtx)
        }
    }

    /** Refresh feeds on open if any are stale (older than an hour), without blocking. */
    private fun maybeAutoSyncSubscriptions() {
        if (!repo.usingSystemCalendar || subscriptions.isEmpty()) return
        val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000
        if (subscriptions.any { it.lastSyncEpoch < hourAgo }) refreshSubscriptions()
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: "Subscription"

    fun updateDarkTheme(dark: Boolean) {
        darkTheme = dark
        prefs.darkTheme = dark
    }

    /** The app's single fixed accent — Nothing red. */
    val accent: Color = Color(0xFFD71921)

    // Snapshot-backed so any composable that reads it recomposes on toggle.
    private val visibility = mutableStateMapOf<String, Boolean>().apply {
        Calendars.all.forEach { put(it.id, prefs.isCalendarVisible(it.id)) }
    }

    init {
        viewModelScope.launch { repo.refresh(today) }
        maybeAutoSyncSubscriptions()
        viewModelScope.launch {
            while (true) {
                _now.value = LocalDateTime.now()
                delay(30_000)
            }
        }
        // Keep alarms and the home-screen widget in sync with the event store.
        // Runs off the main thread — syncAll walks the whole event list and
        // talks to AlarmManager, which would jank scrolling if done on Main.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            repo.events.collectLatest { list ->
                val ctx = getApplication<Application>()
                ReminderScheduler.syncAll(ctx, list)
                AppWidgets.refreshAll(ctx)
            }
        }
    }

    /** Re-read the store — call on resume and after granting calendar permission. */
    fun onResume() {
        viewModelScope.launch { repo.refresh(today) }
        maybeAutoSyncSubscriptions()
    }

    /** The calendar new events default into (primary/first writable). */
    fun defaultCalendarId(): String {
        val cals = calendars.value
        return cals.firstOrNull { it.isPrimary && it.isWritable }?.id
            ?: cals.firstOrNull { it.isWritable }?.id
            ?: Calendars.PERSONAL.id
    }

    // ---------------- queries ----------------

    fun isCalendarVisible(id: String): Boolean = visibility[id] != false

    fun toggleCalendarVisible(id: String) {
        val next = !isCalendarVisible(id)
        visibility[id] = next
        prefs.setCalendarVisible(id, next)
    }

    // Filtering + dedup + day indexing scan the full event list (thousands of
    // instances with several accounts connected), so results are memoized and
    // only recomputed when the event list or calendar visibility changes —
    // recompositions and scrolling hit the cache.
    private var visCacheInput: List<EventItem>? = null
    private var visCacheVis: Map<String, Boolean>? = null
    private var visCache: List<EventItem> = emptyList()

    fun visibleEvents(all: List<EventItem>): List<EventItem> {
        val vis = visibility.toMap() // snapshot read keeps composition tracking on toggles
        if (visCacheInput !== all || visCacheVis != vis) {
            visCache = dedupAcrossCalendars(all.filter { vis[it.calendarId] != false })
            visCacheInput = all
            visCacheVis = vis
        }
        return visCache
    }

    /**
     * Collapses copies of the same event that arrive from multiple connected
     * accounts — e.g. each Google account carries its own "Holidays" calendar,
     * so a holiday would otherwise render two or three times. Runs after the
     * visibility filter so hiding one account's calendar keeps the other copy.
     * Same-calendar twins are kept: those are distinct user-created events.
     */
    private fun dedupAcrossCalendars(list: List<EventItem>): List<EventItem> {
        val ownerByKey = HashMap<String, String>()
        return list.filter { e ->
            val key = "${e.title.trim().lowercase()}|${e.start}|${e.end}|${e.allDay}"
            val owner = ownerByKey.putIfAbsent(key, e.calendarId)
            owner == null || owner == e.calendarId
        }
    }

    private val dayComparator =
        compareByDescending<EventItem> { it.allDay }.thenBy { it.start }

    fun eventsOn(all: List<EventItem>, date: LocalDate): List<EventItem> =
        buildDayIndex(all)[date].orEmpty()

    private var indexCacheInput: List<EventItem>? = null
    private var indexCache: Map<LocalDate, List<EventItem>> = emptyMap()

    /**
     * One-pass index of visible events keyed by every day they cover. Lets the
     * month grid, week columns, day view and agenda do O(1) lookups instead of
     * re-scanning all events per cell.
     */
    fun buildDayIndex(all: List<EventItem>): Map<LocalDate, List<EventItem>> {
        val visible = visibleEvents(all)
        if (indexCacheInput !== visible) {
            val map = HashMap<LocalDate, MutableList<EventItem>>()
            for (e in visible) {
                var d = e.startDate
                var guard = 0
                while (!d.isAfter(e.endDate) && guard < 400) {
                    map.getOrPut(d) { mutableListOf() }.add(e)
                    d = d.plusDays(1)
                    guard++
                }
            }
            map.values.forEach { it.sortWith(dayComparator) }
            indexCacheInput = visible
            indexCache = map
        }
        return indexCache
    }

    fun timedEvents(all: List<EventItem>): List<EventItem> = visibleEvents(all).filterNot { it.allDay }

    fun ongoingEvent(all: List<EventItem>, at: LocalDateTime): EventItem? =
        timedEvents(all).firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }

    fun nextEvent(all: List<EventItem>, at: LocalDateTime): EventItem? =
        timedEvents(all).filter { it.start.isAfter(at) }.minByOrNull { it.start }

    // ---------------- navigation ----------------

    fun go(screen: Screen) { state = state.copy(screen = screen) }
    fun backToApp() { state = state.copy(screen = Screen.APP) }
    fun setView(view: ViewMode) { state = state.copy(view = view) }
    fun toggleAgenda() {
        state = state.copy(view = if (state.view == ViewMode.AGENDA) ViewMode.MONTH else ViewMode.AGENDA)
    }
    fun openEvent(id: String) { state = state.copy(selId = id, screen = Screen.DETAIL) }
    fun closeDetail() { state = state.copy(screen = Screen.APP, selId = null) }
    fun selectDay(day: LocalDate) { state = state.copy(selDay = day) }

    /** True when the system back button has something to pop (otherwise it exits the app). */
    val canGoBack: Boolean
        get() = state.pickerOpen || state.menuEventId != null || state.screen != Screen.APP

    /** Handle a system back press. Returns true if it was consumed. */
    fun onBack(): Boolean {
        val s = state
        return when {
            s.menuEventId != null -> { closeMenu(); true }
            s.pickerOpen -> { closePicker(); true }
            s.screen == Screen.EDITOR -> { cancelEdit(); true }
            s.screen == Screen.DETAIL -> { closeDetail(); true }
            s.screen == Screen.ACCOUNTS -> { go(Screen.SETTINGS); true }
            s.screen != Screen.APP -> { backToApp(); true }
            else -> false
        }
    }

    fun goPrev() {
        state = when (state.view) {
            ViewMode.MONTH -> state.copy(anchor = state.anchor.minusMonths(1))
            ViewMode.WEEK -> state.copy(selDay = state.selDay.minusWeeks(1))
            ViewMode.DAY -> state.copy(selDay = state.selDay.minusDays(1))
            ViewMode.AGENDA -> state
        }
    }

    fun goNext() {
        state = when (state.view) {
            ViewMode.MONTH -> state.copy(anchor = state.anchor.plusMonths(1))
            ViewMode.WEEK -> state.copy(selDay = state.selDay.plusWeeks(1))
            ViewMode.DAY -> state.copy(selDay = state.selDay.plusDays(1))
            ViewMode.AGENDA -> state
        }
    }

    fun goToday() { state = state.copy(anchor = today, selDay = today) }

    /** Jump into the day timeline for [day] — the "open this day's events" action. */
    fun openDayView(day: LocalDate) { state = state.copy(selDay = day, anchor = day, view = ViewMode.DAY) }

    // ---------------- month/year picker ----------------

    fun openPicker() { state = state.copy(pickerOpen = true, pickerYear = state.anchor.year) }
    fun closePicker() { state = state.copy(pickerOpen = false) }
    fun shiftPickerYear(delta: Int) { state = state.copy(pickerYear = state.pickerYear + delta) }
    fun pickYear(year: Int) { state = state.copy(pickerYear = year) }
    fun selectMonth(monthIndex0: Int) {
        val d = LocalDate.of(state.pickerYear, monthIndex0 + 1, 1)
        state = state.copy(anchor = d, selDay = d, pickerOpen = false)
    }
    fun pickerJumpToday() { state = state.copy(anchor = today, selDay = today, pickerOpen = false) }

    // ---------------- editor ----------------

    fun blankForm(day: LocalDate = state.selDay) = EventForm(
        id = null,
        title = "",
        calendarId = defaultCalendarId(),
        allDay = false,
        startDate = day,
        startTime = LocalTime.of(10, 0),
        endDate = day,
        endTime = LocalTime.of(11, 0),
        repeat = RepeatRule.NONE,
        reminders = listOf(defaultReminder),
        location = "",
        notes = "",
    )

    private fun formFromEvent(e: EventItem) = EventForm(
        id = e.id,
        title = e.title,
        calendarId = e.calendarId,
        allDay = e.allDay,
        startDate = e.startDate,
        startTime = if (e.allDay) LocalTime.of(10, 0) else e.start.toLocalTime(),
        endDate = e.endDate,
        endTime = if (e.allDay) LocalTime.of(11, 0) else e.end.toLocalTime(),
        repeat = e.repeat,
        reminders = e.reminders,
        location = e.location ?: "",
        notes = e.notes ?: "",
    )

    fun openNewEvent(day: LocalDate = state.selDay) {
        state = state.copy(form = blankForm(day), editId = null, screen = Screen.EDITOR)
    }

    fun openEdit(all: List<EventItem>) {
        val e = all.find { it.id == state.selId } ?: return
        state = state.copy(form = formFromEvent(e), editId = e.id, screen = Screen.EDITOR)
    }

    fun patchForm(patch: (EventForm) -> EventForm) {
        val f = state.form ?: return
        state = state.copy(form = patch(f))
    }

    fun cancelEdit() {
        state = state.copy(screen = if (state.editId != null) Screen.DETAIL else Screen.APP, form = null)
    }

    fun saveEvent() {
        val f = state.form ?: return
        val wasEditing = state.editId != null
        val id = state.editId ?: "n${System.currentTimeMillis()}"
        val start = if (f.allDay) f.startDate.atStartOfDay() else LocalDateTime.of(f.startDate, f.startTime)
        val end = if (f.allDay) f.endDate.atStartOfDay() else LocalDateTime.of(f.endDate, f.endTime)
        val event = EventItem(
            id = id,
            title = f.title.ifBlank { "Untitled" },
            calendarId = f.calendarId,
            start = start,
            end = end,
            allDay = f.allDay,
            repeat = f.repeat,
            reminders = f.reminders,
            location = f.location.ifBlank { null },
            notes = f.notes.ifBlank { null },
        )
        viewModelScope.launch {
            val newId = repo.save(event, f.calendarId)
            state = state.copy(
                screen = if (wasEditing) Screen.DETAIL else Screen.APP,
                selId = newId,
                selDay = f.startDate,
                anchor = f.startDate,
                form = null,
                editId = null,
            )
        }
    }

    fun deleteEvent() {
        val id = state.selId ?: return
        viewModelScope.launch { repo.delete(id) }
        state = state.copy(screen = Screen.APP, selId = null)
    }

    fun deleteById(id: String) {
        viewModelScope.launch { repo.delete(id) }
        state = state.copy(menuEventId = null)
    }

    fun dupById(all: List<EventItem>, id: String) {
        val e = all.find { it.id == id } ?: return
        val newId = "n${System.currentTimeMillis()}"
        val copy = e.copy(id = newId, repeat = RepeatRule.NONE, isRecurring = false)
        viewModelScope.launch {
            val savedId = repo.save(copy, copy.calendarId)
            state = state.copy(menuEventId = null, selId = savedId, screen = Screen.DETAIL)
        }
    }

    // ---------------- drag reschedule (day view) ----------------

    fun setDrag(drag: DragState?) { state = state.copy(drag = drag) }

    fun shiftEvent(all: List<EventItem>, id: String, deltaMinutes: Int) {
        val e = all.find { it.id == id } ?: return
        val durMin = ChronoUnit.MINUTES.between(e.start, e.end).toInt().coerceAtLeast(15)
        val startMin = (e.start.toLocalTime().toSecondOfDay() / 60 + deltaMinutes)
            .coerceIn(0, 24 * 60 - durMin)
        val newStart = e.startDate.atStartOfDay().plusMinutes(startMin.toLong())
        val newEnd = newStart.plusMinutes(durMin.toLong())
        viewModelScope.launch { repo.save(e.copy(start = newStart, end = newEnd), e.calendarId) }
        state = state.copy(drag = null)
    }

    // ---------------- swipe delete (agenda) + long-press menu ----------------

    fun setSwipe(swipe: SwipeState?) { state = state.copy(swipe = swipe) }
    fun openMenu(id: String) { state = state.copy(menuEventId = id) }
    fun closeMenu() { state = state.copy(menuEventId = null) }

    // ---------------- search ----------------

    fun onSearchQuery(q: String) { state = state.copy(searchQuery = q) }

    fun toggleSearchCalendar(id: String) {
        val cur = state.searchCalendars
        state = state.copy(searchCalendars = if (id in cur) cur - id else cur + id)
    }

    fun setSearchRange(range: SearchRange) { state = state.copy(searchRange = range) }

    val searchFiltersActive: Boolean
        get() = state.searchCalendars.isNotEmpty() || state.searchRange != SearchRange.ALL

    fun searchResults(all: List<EventItem>): List<EventItem> {
        val q = state.searchQuery.trim().lowercase()
        if (q.isBlank() && !searchFiltersActive) return emptyList()

        var res = dedupAcrossCalendars(all)
        if (state.searchCalendars.isNotEmpty()) {
            res = res.filter { it.calendarId in state.searchCalendars }
        }
        res = when (state.searchRange) {
            SearchRange.ALL -> res
            SearchRange.NEXT7 -> res.filter { !it.startDate.isBefore(today) && !it.startDate.isAfter(today.plusDays(7)) }
            SearchRange.NEXT30 -> res.filter { !it.startDate.isBefore(today) && !it.startDate.isAfter(today.plusDays(30)) }
            SearchRange.PAST -> res.filter { it.startDate.isBefore(today) }
        }
        if (q.isNotBlank()) {
            // Titles only — matching notes/descriptions surfaced too much noise
            // (holiday calendars carry long descriptions).
            res = res.filter { it.title.lowercase().contains(q) }
        }
        return res.sortedBy { it.start }
    }

    var recentSearches by mutableStateOf(prefs.recentSearches)
        private set

    /** Open a result and remember the query that found it (latest first, deduped). */
    fun openSearchResult(id: String) {
        val q = state.searchQuery.trim()
        if (q.isNotEmpty()) {
            val next = (listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) }).take(8)
            recentSearches = next
            prefs.recentSearches = next
        }
        openEvent(id)
    }

    // ---------------- settings ----------------

    fun toggleNdot() {
        ndot = !ndot
        prefs.ndotNumerals = ndot
    }

    fun updateWeekStart(v: Int) {
        weekStart = v
        prefs.weekStart = v
    }

    /** Cycle the default reminder used for newly created events. */
    fun cycleDefaultReminder() {
        val options = listOf(0, 5, 10, 15, 30, 60, 1440)
        val idx = options.indexOf(defaultReminder).let { if (it < 0) 2 else it }
        val next = options[(idx + 1) % options.size]
        defaultReminder = next
        prefs.defaultReminderMinutes = next
    }
}
