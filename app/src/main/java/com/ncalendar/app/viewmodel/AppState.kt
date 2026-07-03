package com.ncalendar.app.viewmodel

import com.ncalendar.app.data.RepeatRule
import java.time.LocalDate
import java.time.LocalTime

enum class Screen { APP, DETAIL, EDITOR, ACCOUNTS, SETTINGS, SEARCH }
enum class ViewMode { MONTH, WEEK, DAY, AGENDA }
enum class SearchRange(val label: String) { ALL("All"), NEXT7("Next 7d"), NEXT30("Next 30d"), PAST("Past") }

data class DragState(val eventId: String, val deltaMinutes: Int)
data class SwipeState(val eventId: String, val dx: Float)

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
    val reminders: List<Int>,
    val location: String,
    val notes: String,
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
)
