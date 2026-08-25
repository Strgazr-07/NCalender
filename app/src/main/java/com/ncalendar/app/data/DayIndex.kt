package com.ncalendar.app.data

import java.time.LocalDate

/**
 * One-pass index of events keyed by every day they cover. Lets the month grid, week columns,
 * day view and agenda do O(1) lookups instead of re-scanning all events per cell. Extracted out
 * of CalendarViewModel so the day-span math (in particular the midnight-end edge case — see
 * DayIndexTest) is unit-testable without a ViewModel.
 */
object DayIndex {

    private val dayComparator = compareByDescending<EventItem> { it.allDay }.thenBy { it.start }

    /** The inclusive last day an event should appear on. A timed event ending exactly at the
     *  next day's midnight (e.g. 22:00-24:00) is a same-day event, not a two-day one — without
     *  this, it indexes onto both days and DayView paints a phantom zero-length block at 00:00
     *  on the day after. All-day events already carry an inclusive endDate, so they're untouched. */
    private fun lastDay(e: EventItem): LocalDate =
        if (!e.allDay && e.end.toLocalTime() == java.time.LocalTime.MIDNIGHT && e.endDate.isAfter(e.startDate))
            e.endDate.minusDays(1)
        else e.endDate

    fun build(events: List<EventItem>): Map<LocalDate, List<EventItem>> {
        val map = HashMap<LocalDate, MutableList<EventItem>>()
        for (e in events) {
            var d = e.startDate
            val last = lastDay(e)
            var guard = 0
            while (!d.isAfter(last) && guard < 800) {
                map.getOrPut(d) { mutableListOf() }.add(e)
                d = d.plusDays(1)
                guard++
            }
        }
        map.values.forEach { it.sortWith(dayComparator) }
        return map
    }
}
