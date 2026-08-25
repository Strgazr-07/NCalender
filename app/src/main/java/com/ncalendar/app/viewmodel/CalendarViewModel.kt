package com.ncalendar.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ncalendar.app.data.CalendarInfo
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.data.accountKey
import com.ncalendar.app.data.DarkBackgroundStyle
import com.ncalendar.app.data.DayIndex
import com.ncalendar.app.data.EditScope
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.EventRepository
import com.ncalendar.app.data.Prefs
import com.ncalendar.app.data.Recurrence
import com.ncalendar.app.data.RepeatRule
import com.ncalendar.app.data.ThemeMode
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
    var allDayReminderTime by mutableStateOf(prefs.allDayReminderTime)
        private set
    var liveUpdates by mutableStateOf(prefs.liveUpdates)
        private set
    var syncRemindersToProvider by mutableStateOf(prefs.syncRemindersToProvider)
        private set
    var showHolidays by mutableStateOf(prefs.showHolidays)
        private set

    fun updateShowHolidays(enabled: Boolean) {
        showHolidays = enabled
        prefs.showHolidays = enabled
        refreshAfterFilterChange()
    }
    var darkTheme by mutableStateOf(prefs.darkTheme)
        private set
    var themeMode by mutableStateOf(prefs.themeMode)
        private set
    var darkBackgroundStyle by mutableStateOf(prefs.darkBackgroundStyle)
        private set
    var accent by mutableStateOf(Color(prefs.accentColorArgb))
        private set

    /** Privacy opt-out: never read/write the system (Google) calendars; Room only. */
    var localOnly by mutableStateOf(prefs.localOnly)
        private set

    fun updateLocalOnly(v: Boolean) {
        prefs.localOnly = v
        localOnly = v
        viewModelScope.launch { repo.refresh(today, force = true) }
    }

    // ---------------- .ics subscriptions ----------------

    /** The subscribed-feed registry (mirrored events live in the system calendar). */
    var subscriptions by mutableStateOf(prefs.icsSubscriptions)
        private set
    var syncingSubs by mutableStateOf(false)
        private set
    var importMessage by mutableStateOf<String?>(null)
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
            repo.refresh(today, force = true)
            AppWidgets.refreshAll(appCtx)
            syncingSubs = false
        }
    }

    fun refreshSubscriptions() {
        if (subscriptions.isEmpty() || syncingSubs) return
        viewModelScope.launch {
            syncingSubs = true
            subscriptions = IcsSyncManager.syncAll(appCtx)
            repo.refresh(today, force = true)
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
            repo.refresh(today, force = true)
            AppWidgets.refreshAll(appCtx)
        }
    }

    fun importIcs(uri: Uri, calendarId: String) {
        viewModelScope.launch {
            val n = repo.importIcs(uri, calendarId)
            importMessage = if (n > 0) "Imported $n events" else "No events imported"
            AppWidgets.refreshAll(appCtx)
        }
    }

    fun clearImportMessage() {
        importMessage = null
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
        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
        prefs.darkTheme = dark
        prefs.themeMode = themeMode
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.themeMode = mode
        if (mode != ThemeMode.SYSTEM) {
            darkTheme = mode == ThemeMode.DARK
            prefs.darkTheme = darkTheme
        }
        // Widgets read the same theme pref but have no way to observe it — without an explicit
        // repaint they'd keep their old colors until their next 30-minute tick.
        AppWidgets.refreshAll(appCtx)
    }

    fun updateDarkBackgroundStyle(style: DarkBackgroundStyle) {
        darkBackgroundStyle = style
        prefs.darkBackgroundStyle = style
    }

    fun updateAccent(argb: Int) {
        accent = Color(argb)
        prefs.accentColorArgb = argb
        AppWidgets.refreshAll(appCtx)
    }

    // Snapshot-backed so any composable that reads it recomposes on toggle. Hydrated from
    // EVERY known calendar/account via the repo.calendars collector in init{} below — NOT
    // seeded here from Calendars.all, which was the source of a real bug: it only ever knew
    // about the 3 demo calendars, so hiding a real (numeric) system calendar id was written to
    // prefs but never read back, and silently reappeared on the next app start.
    private val visibility = mutableStateMapOf<String, Boolean>()
    private val accountVisibility = mutableStateMapOf<String, Boolean>()

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
        viewModelScope.launch {
            repo.calendars.collect { cals ->
                // Subscribed-feed calendars are a device-local mirror, not a real account —
                // CalendarsScreen already excludes them from the account grouping, and the
                // first-run picker below must too.
                val real = cals.filterNot { it.accountName == SubscriptionCalendars.ACCOUNT_NAME }
                visibility.clear()
                cals.forEach { visibility[it.id] = prefs.isCalendarVisible(it.id) }
                accountVisibility.clear()
                real.map { it.accountKey }.distinct().forEach { accountVisibility[it] = prefs.isAccountVisible(it) }
                maybeShowAccountPicker(real)
            }
        }
    }

    /**
     * First sync after granting calendar access: shows a "these will show — uncheck any you
     * don't want" review, since a default-all-on denylist otherwise dumps every calendar from
     * every connected account straight into the UI with no way to tell in advance. Fires at
     * most once — [finishAccountPicker] latches [Prefs.calendarFilterInitialized] so this
     * never re-triggers on a later refresh (e.g. after the picker itself, or after a plain
     * account re-sync).
     */
    private fun maybeShowAccountPicker(realCalendars: List<CalendarInfo>) {
        if (prefs.calendarFilterInitialized) return
        if (!repo.usingSystemCalendar) return
        if (state.screen != Screen.APP) return // don't yank the user off whatever they're doing
        // repo.calendars starts as (and falls back to) the built-in offline set, whose ids are
        // slugs rather than the provider's numeric ones. Without this check the picker could
        // fire on that placeholder list during the window before the first real query lands,
        // showing three calendars the user doesn't have.
        val synced = realCalendars.filter { it.id.toLongOrNull() != null }
        if (synced.isEmpty()) return // nothing synced yet — wait for a later emission
        val accounts = synced.map { it.accountKey }.distinct()
        if (accounts.size <= 1 && synced.size <= 2) {
            // Nothing worth reviewing — one account, at most a couple of calendars.
            prefs.calendarFilterInitialized = true
            return
        }
        state = state.copy(screen = Screen.ACCOUNT_PICKER)
    }

    fun finishAccountPicker() {
        prefs.calendarFilterInitialized = true
        state = state.copy(screen = Screen.APP)
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
    fun isAccountVisible(accountKey: String): Boolean = accountVisibility[accountKey] != false

    /** Calendar/account visibility now lives at the query level (EventRepository filters at
     *  the CalendarContract query itself), so a toggle needs an actual reload — it can't just
     *  re-filter an in-memory list the way it used to. */
    fun toggleCalendarVisible(id: String) {
        val next = !isCalendarVisible(id)
        visibility[id] = next
        prefs.setCalendarVisible(id, next)
        refreshAfterFilterChange()
    }

    fun toggleAccountVisible(accountKey: String) {
        val next = !isAccountVisible(accountKey)
        accountVisibility[accountKey] = next
        prefs.setAccountVisible(accountKey, next)
        refreshAfterFilterChange()
    }

    private fun refreshAfterFilterChange() {
        viewModelScope.launch {
            repo.refresh(today, force = true)
            AppWidgets.refreshAll(appCtx)
        }
    }

    // Dedup scans the full event list (thousands of instances with several accounts
    // connected), so results are memoized against list identity — recompositions and
    // scrolling hit the cache. Calendar/account visibility is already applied server-side
    // (EventRepository filters at the CalendarContract query level, or in-memory for the
    // local-only store), so `all` only ever contains events from currently-visible
    // calendars — this just collapses the cross-account duplicates that survive that filter.
    private var visCacheInput: List<EventItem>? = null
    private var visCache: List<EventItem> = emptyList()

    fun visibleEvents(all: List<EventItem>): List<EventItem> {
        if (visCacheInput !== all) {
            visCache = dedupAcrossCalendars(all)
            visCacheInput = all
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

    fun eventsOn(all: List<EventItem>, date: LocalDate): List<EventItem> =
        buildDayIndex(all)[date].orEmpty()

    private var indexCacheInput: List<EventItem>? = null
    private var indexCache: Map<LocalDate, List<EventItem>> = emptyMap()

    /**
     * One-pass index of visible events keyed by every day they cover. Lets the
     * month grid, week columns, day view and agenda do O(1) lookups instead of
     * re-scanning all events per cell. The day-span math lives in [DayIndex] so
     * it's unit-testable; this wrapper only memoizes against list identity.
     */
    fun buildDayIndex(all: List<EventItem>): Map<LocalDate, List<EventItem>> {
        val visible = visibleEvents(all)
        if (indexCacheInput !== visible) {
            indexCache = DayIndex.build(visible)
            indexCacheInput = visible
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
    /** Selecting a spill-over cell (a greyed day from the previous/next month in the grid)
     *  moves the anchor with it — otherwise the selection ring lands on a dimmed out-of-month
     *  cell and the grid stays on the old month, which reads as the tap having half-worked. */
    fun selectDay(day: LocalDate) {
        val sameMonth = day.year == state.anchor.year && day.monthValue == state.anchor.monthValue
        state = state.copy(selDay = day, anchor = if (sameMonth) state.anchor else day)
    }
    fun openDayEvents(day: LocalDate = state.selDay) { state = state.copy(selDay = day, anchor = day, screen = Screen.DAY_EVENTS) }

    /** True when the system back button has something to pop (otherwise it exits the app). */
    val canGoBack: Boolean
        get() = state.pendingScopeAction != null || state.pickerOpen || state.menuEventId != null || state.screen != Screen.APP

    /** Handle a system back press. Returns true if it was consumed. */
    fun onBack(): Boolean {
        val s = state
        return when {
            s.pendingScopeAction != null -> { cancelScope(); true }
            s.menuEventId != null -> { closeMenu(); true }
            s.pickerOpen -> { closePicker(); true }
            s.screen == Screen.ACCOUNT_PICKER -> { finishAccountPicker(); true }
            s.screen == Screen.EDITOR -> { cancelEdit(); true }
            s.screen == Screen.DETAIL -> { closeDetail(); true }
            s.screen == Screen.DAY_EVENTS -> { backToApp(); true }
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
        repeatInterval = 1,
        repeatByDays = emptySet(),
        repeatEndDate = null,
        repeatEndCount = null,
        reminders = listOf(defaultReminder),
        location = "",
        notes = "",
    )

    private fun formFromEvent(
        e: EventItem,
        scope: EditScope = EditScope.ALL_EVENTS,
        instanceId: String? = null,
    ) = EventForm(
        id = e.id,
        title = e.title,
        calendarId = e.calendarId,
        allDay = e.allDay,
        startDate = e.startDate,
        startTime = if (e.allDay) LocalTime.of(10, 0) else e.start.toLocalTime(),
        endDate = e.endDate,
        endTime = if (e.allDay) LocalTime.of(11, 0) else e.end.toLocalTime(),
        repeat = e.repeat,
        repeatInterval = e.repeatInterval,
        repeatByDays = e.repeatByDays,
        repeatEndDate = e.repeatEndDate,
        repeatEndCount = e.repeatEndCount,
        reminders = e.reminders,
        location = e.location ?: "",
        notes = e.notes ?: "",
        scope = scope,
        instanceId = instanceId,
    )

    fun openNewEvent(day: LocalDate = state.selDay) {
        state = state.copy(form = blankForm(day), editId = null, screen = Screen.EDITOR)
    }

    fun openNewEvent(day: LocalDate, startTime: LocalTime?, endTime: LocalTime?) {
        val base = blankForm(day)
        state = state.copy(
            form = base.copy(
                startTime = startTime ?: base.startTime,
                endTime = endTime ?: base.endTime,
            ),
            editId = null,
            screen = Screen.EDITOR,
        )
    }

    /** Edit button on DetailScreen/LongPressMenu. A recurring event routes through the scope
     *  sheet first; formFromEvent only fires once a scope is actually chosen (see
     *  [confirmScope]) since ALL_EVENTS needs an async lookup of the series' true dates. */
    fun openEdit(all: List<EventItem>) {
        val e = all.find { it.id == state.selId } ?: return
        if (e.isRecurring) {
            state = state.copy(pendingScopeAction = PendingScopeAction.Edit(e.id))
        } else {
            beginEdit(e, EditScope.ALL_EVENTS)
        }
    }

    private fun beginEdit(e: EventItem, scope: EditScope) {
        viewModelScope.launch {
            // ALL_EVENTS on a recurring event must show the SERIES' own dates, not this
            // instance's — saving an instance's date back as DTSTART would move the whole
            // series (see EventRepository.save's THIS_EVENT branch and why it exists).
            val target = if (scope == EditScope.ALL_EVENTS && e.isRecurring) repo.seriesBase(e) ?: e else e
            state = state.copy(form = formFromEvent(target, scope, e.id), editId = e.id, screen = Screen.EDITOR)
        }
    }

    fun patchForm(patch: (EventForm) -> EventForm) {
        val f = state.form ?: return
        state = state.copy(form = patch(f))
    }

    fun cancelEdit() {
        state = state.copy(screen = if (state.editId != null) Screen.DETAIL else Screen.APP, form = null)
    }

    fun clearError() { state = state.copy(errorMessage = null) }

    fun saveEvent() {
        val f = state.form ?: return
        val wasEditing = state.editId != null
        // For a THIS_EVENT save, the event id must be the ORIGINAL instance id — it carries
        // the original occurrence date/time EventRepository.save needs to split just this one
        // occurrence out (ORIGINAL_INSTANCE_TIME on system calendars, the exception date on
        // local ones). ALL_EVENTS (or a non-recurring event) uses the normal series/new id.
        val id = if (f.scope == EditScope.THIS_EVENT) {
            f.instanceId ?: state.editId ?: "n${System.currentTimeMillis()}"
        } else {
            state.editId ?: "n${System.currentTimeMillis()}"
        }
        val start = if (f.allDay) f.startDate.atStartOfDay() else LocalDateTime.of(f.startDate, f.startTime)
        val end = if (f.allDay) f.endDate.atStartOfDay() else LocalDateTime.of(f.endDate, f.endTime)
        val event = EventItem(
            id = id,
            title = f.title.ifBlank { "Untitled" },
            calendarId = f.calendarId,
            start = start,
            end = end,
            allDay = f.allDay,
            repeat = if (f.scope == EditScope.THIS_EVENT) RepeatRule.NONE else f.repeat,
            repeatInterval = if (f.scope == EditScope.THIS_EVENT) 1 else f.repeatInterval,
            repeatByDays = if (f.scope == EditScope.THIS_EVENT) emptySet() else f.repeatByDays,
            repeatEndDate = if (f.scope == EditScope.THIS_EVENT) null else f.repeatEndDate,
            repeatEndCount = if (f.scope == EditScope.THIS_EVENT) null else f.repeatEndCount,
            reminders = f.reminders,
            location = f.location.ifBlank { null },
            notes = f.notes.ifBlank { null },
        )
        viewModelScope.launch {
            val savedId = repo.save(event, f.calendarId, f.scope)
            if (savedId == null) {
                // The provider refused to split this occurrence out (e.g. a read-only
                // calendar) — never fall back to silently editing the whole series instead.
                state = state.copy(errorMessage = "Couldn't save just this event — try editing the whole series instead.")
                return@launch
            }
            val resolvedId = resolveSavedInstanceId(savedId, event, f.scope)
            state = state.copy(
                screen = if (wasEditing) Screen.DETAIL else Screen.APP,
                selId = resolvedId,
                selDay = f.startDate,
                anchor = f.startDate,
                form = null,
                editId = null,
            )
        }
    }

    /** repo.save on a recurring series returns the SERIES' base id, but every event actually
     *  shown to the user carries the "baseId::begin" instance suffix — so naively using the
     *  base id as selId leaves DetailScreen unable to find it (events.find returns null) and
     *  it immediately bounces back to the calendar. Resolve to the freshly-saved instance
     *  that matches the edited occurrence's own date instead. */
    private fun resolveSavedInstanceId(savedId: String, edited: EventItem, scope: EditScope): String {
        if (scope == EditScope.THIS_EVENT || edited.repeat == RepeatRule.NONE) return savedId
        val fresh = events.value
        return fresh.firstOrNull { Recurrence.baseId(it.id) == savedId && it.startDate == edited.startDate }?.id
            ?: fresh.firstOrNull { Recurrence.baseId(it.id) == savedId }?.id
            ?: savedId
    }

    fun deleteEvent() {
        val id = state.selId ?: return
        requestDelete(id)
    }

    fun deleteById(id: String) {
        requestDelete(id)
    }

    private fun requestDelete(id: String) {
        val e = events.value.find { it.id == id }
        if (e?.isRecurring == true) {
            state = state.copy(pendingScopeAction = PendingScopeAction.Delete(id))
        } else {
            deleteWithScope(id, EditScope.ALL_EVENTS)
        }
    }

    private fun deleteWithScope(id: String, scope: EditScope) {
        viewModelScope.launch { repo.delete(id, scope) }
        state = state.copy(
            menuEventId = null,
            selId = if (state.selId == id) null else state.selId,
            screen = if (state.selId == id && state.screen == Screen.DETAIL) Screen.APP else state.screen,
        )
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

    // ---------------- recurring-series scope sheet ----------------

    /** The user picked THIS_EVENT or ALL_EVENTS for whatever [state.pendingScopeAction] holds. */
    fun confirmScope(all: List<EventItem>, scope: EditScope) {
        val action = state.pendingScopeAction ?: return
        state = state.copy(pendingScopeAction = null)
        when (action) {
            is PendingScopeAction.Edit -> all.find { it.id == action.id }?.let { beginEdit(it, scope) }
            is PendingScopeAction.Delete -> deleteWithScope(action.id, scope)
            is PendingScopeAction.Shift -> all.find { it.id == action.id }?.let { performShift(it, action.deltaMinutes, scope) }
            is PendingScopeAction.Resize -> all.find { it.id == action.id }?.let { performResize(it, action.deltaMinutes, scope) }
        }
    }

    fun cancelScope() { state = state.copy(pendingScopeAction = null) }

    // ---------------- drag reschedule (day view) ----------------

    fun setDrag(drag: DragState?) { state = state.copy(drag = drag) }

    fun shiftEvent(all: List<EventItem>, id: String, deltaMinutes: Int) {
        val e = all.find { it.id == id } ?: return
        if (e.isRecurring) {
            state = state.copy(drag = null, pendingScopeAction = PendingScopeAction.Shift(id, deltaMinutes))
            return
        }
        performShift(e, deltaMinutes, EditScope.ALL_EVENTS)
    }

    private fun performShift(e: EventItem, deltaMinutes: Int, scope: EditScope) {
        val durMin = ChronoUnit.MINUTES.between(e.start, e.end).toInt().coerceAtLeast(15)
        val startMin = (e.start.toLocalTime().toSecondOfDay() / 60 + deltaMinutes)
            .coerceIn(0, 24 * 60 - durMin)
        val newStart = e.startDate.atStartOfDay().plusMinutes(startMin.toLong())
        val newEnd = newStart.plusMinutes(durMin.toLong())
        viewModelScope.launch { repo.save(e.copy(start = newStart, end = newEnd), e.calendarId, scope) }
        state = state.copy(drag = null)
    }

    fun resizeEvent(all: List<EventItem>, id: String, deltaMinutes: Int) {
        val e = all.find { it.id == id } ?: return
        if (e.isRecurring) {
            state = state.copy(drag = null, pendingScopeAction = PendingScopeAction.Resize(id, deltaMinutes))
            return
        }
        performResize(e, deltaMinutes, EditScope.ALL_EVENTS)
    }

    private fun performResize(e: EventItem, deltaMinutes: Int, scope: EditScope) {
        val minEnd = e.start.plusMinutes(15)
        val maxEnd = e.startDate.atStartOfDay().plusDays(1)
        val newEnd = e.end.plusMinutes(deltaMinutes.toLong()).coerceAtLeast(minEnd).coerceAtMost(maxEnd)
        viewModelScope.launch { repo.save(e.copy(end = newEnd), e.calendarId, scope) }
        state = state.copy(drag = null)
    }

    fun conflictsFor(all: List<EventItem>, form: EventForm): List<EventItem> {
        if (form.allDay) return emptyList()
        val start = LocalDateTime.of(form.startDate, form.startTime)
        val end = LocalDateTime.of(form.endDate, form.endTime)
        if (!end.isAfter(start)) return emptyList()
        return visibleEvents(all)
            .filterNot { it.allDay }
            .filter { Recurrence.baseId(it.id) != form.id?.let(Recurrence::baseId) }
            .filter { it.start.isBefore(end) && it.end.isAfter(start) }
            .sortedBy { it.start }
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

    fun updateDefaultReminder(minutes: Int) {
        defaultReminder = minutes
        prefs.defaultReminderMinutes = minutes
    }

    fun updateAllDayReminderTime(time: LocalTime) {
        allDayReminderTime = time
        prefs.allDayReminderTime = time
        // Every armed all-day alarm was computed against the old time, so they all need
        // re-arming — syncAll's fingerprint check won't catch this on its own since neither
        // the events nor their reminder lists changed.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            ReminderScheduler.resyncAll(appCtx, events.value)
        }
    }

    /** Per-calendar default reminders — the path for read-only synced calendars (Birthdays,
     *  Holidays) whose events carry no reminders of their own and can't be edited to add one. */
    fun calendarDefaultReminders(calendarId: String): List<Int> = prefs.calendarDefaultReminders(calendarId)

    fun setCalendarDefaultReminders(calendarId: String, minutes: List<Int>) {
        prefs.setCalendarDefaultReminders(calendarId, minutes)
        viewModelScope.launch {
            repo.refresh(today, force = true)
        }
    }

    /** Sets reminders on a single event without editing the event itself — works on read-only
     *  calendars, where the normal save path would be rejected by the provider. */
    fun setEventReminders(id: String, minutes: List<Int>) {
        viewModelScope.launch { repo.setReminders(id, minutes) }
    }

    fun updateLiveUpdates(enabled: Boolean) {
        liveUpdates = enabled
        prefs.liveUpdates = enabled
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            // syncAll owns arming/clearing the live chip, so just re-run it.
            ReminderScheduler.syncAll(appCtx, events.value)
        }
    }

    fun updateSyncRemindersToProvider(enabled: Boolean) {
        syncRemindersToProvider = enabled
        prefs.syncRemindersToProvider = enabled
        // Applied to existing events too, not just ones edited from here on.
        viewModelScope.launch { repo.applyProviderReminderSync(enabled) }
    }
}
