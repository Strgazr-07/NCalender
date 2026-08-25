package com.ncalendar.app.viewmodel

import com.ncalendar.app.data.EditScope
import com.ncalendar.app.data.RepeatRule
import java.time.LocalDate
import java.time.LocalTime

enum class Screen { APP, DETAIL, EDITOR, ACCOUNTS, SETTINGS, SEARCH, DAY_EVENTS, ACCOUNT_PICKER }
enum class ViewMode { MONTH, WEEK, DAY, AGENDA }
enum class SearchRange(val label: String) { ALL("All"), NEXT7("Next 7d"), NEXT30("Next 30d"), PAST("Past") }
enum class DragMode { MOVE, RESIZE_END }

data class DragState(val eventId: String, val deltaMinutes: Int, val mode: DragMode = DragMode.MOVE)
data class SwipeState(val eventId: String, val dx: Float)

/** An edit/delete/drag on a recurring event, waiting on the user to pick a [EditScope] via
 *  NRecurringScopeSheet before it actually runs. */
sealed interface PendingScopeAction {
    data class Edit(val id: String) : PendingScopeAction
    data class Delete(val id: String) : PendingScopeAction
    data class Shift(val id: String, val deltaMinutes: Int) : PendingScopeAction
    data class Resize(val id: String, val deltaMinutes: Int) : PendingScopeAction
}

data class EventForm(
    val id: String?,
    val title: String,
    val calendarId: String,
    val allDay: Boolean,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val repeat: RepeatRule,
    val repeatInterval: Int = 1,
    val repeatByDays: Set<Int> = emptySet(),
    val repeatEndDate: LocalDate? = null,
    val repeatEndCount: Int? = null,
    val reminders: List<Int>,
    val location: String,
    val notes: String,
    /** Which part of a recurring series this edit will write back. Chosen via
     *  NRecurringScopeSheet before the editor opens, so the form can pre-fill the right
     *  date (the instance's own, or the series' true DTSTART). */
    val scope: EditScope = EditScope.ALL_EVENTS,
    /** The instance id the editor was opened from, when [scope] is THIS_EVENT — carries the
     *  ORIGINAL_INSTANCE_TIME the provider needs to split the right occurrence out. */
    val instanceId: String? = null,
)

data class AppState(
    val screen: Screen = Screen.APP,
    val view: ViewMode = ViewMode.MONTH,
    val anchor: LocalDate,
    val selDay: LocalDate,
    val selId: String? = null,
    val editId: String? = null,
    val searchQuery: String = "",
    val searchCalendars: Set<String> = emptySet(),
    val searchRange: SearchRange = SearchRange.ALL,
    val pickerOpen: Boolean = false,
    val pickerYear: Int,
    val drag: DragState? = null,
    val swipe: SwipeState? = null,
    val menuEventId: String? = null,
    val form: EventForm? = null,
    /** Set while the recurring-scope sheet is up; cleared when the user picks or cancels. */
    val pendingScopeAction: PendingScopeAction? = null,
    /** A user-facing failure that needs acknowledging (e.g. the provider refused to split a
     *  single occurrence out of a series on a read-only calendar). */
    val errorMessage: String? = null,
)
