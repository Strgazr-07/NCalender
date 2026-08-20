package com.ncalendar.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayIndexTest {

    private fun timed(id: String, start: LocalDateTime, end: LocalDateTime) = EventItem(
        id = id, title = id, calendarId = "1", start = start, end = end, allDay = false,
    )

    private fun allDay(id: String, start: LocalDate, end: LocalDate) = EventItem(
        id = id, title = id, calendarId = "1",
        start = start.atStartOfDay(), end = end.atStartOfDay(), allDay = true,
    )

    @Test
    fun timedEventEndingExactlyAtNextDayMidnightIndexesOnlyOnItsOwnDay() {
        // A 22:00-24:00 event is a same-day event; it must not paint a phantom block at
        // 00:00 on the following day. This is the DayView.kt:192-196 phantom-block bug.
        val day1 = LocalDate.of(2026, 8, 21)
        val day2 = day1.plusDays(1)
        val event = timed("e1", LocalDateTime.of(day1, java.time.LocalTime.of(22, 0)), day2.atStartOfDay())

        val index = DayIndex.build(listOf(event))

        assertThat(index[day1]).containsExactly(event)
        assertThat(index[day2].orEmpty()).isEmpty()
    }

    @Test
    fun ordinaryMultiDayTimedEventStillSpansBothDays() {
        // An event that genuinely runs into the next day (not just touching midnight) must
        // still index onto every day it covers.
        val day1 = LocalDate.of(2026, 8, 21)
        val day2 = day1.plusDays(1)
        val event = timed(
            "e2",
            LocalDateTime.of(day1, java.time.LocalTime.of(22, 0)),
            LocalDateTime.of(day2, java.time.LocalTime.of(1, 0)),
        )

        val index = DayIndex.build(listOf(event))

        assertThat(index[day1]).containsExactly(event)
        assertThat(index[day2]).containsExactly(event)
    }

    @Test
    fun allDayEventIndexesOnEveryInclusiveDay() {
        val start = LocalDate.of(2026, 12, 24)
        val end = LocalDate.of(2026, 12, 26)
        val event = allDay("e3", start, end)

        val index = DayIndex.build(listOf(event))

        assertThat(index[start]).containsExactly(event)
        assertThat(index[start.plusDays(1)]).containsExactly(event)
        assertThat(index[end]).containsExactly(event)
        assertThat(index[end.plusDays(1)].orEmpty()).isEmpty()
    }

    @Test
    fun allDayEventsSortBeforeTimedEventsOnTheSameDay() {
        val day = LocalDate.of(2026, 8, 21)
        val timedEvent = timed("timed", day.atTime(9, 0), day.atTime(10, 0))
        val allDayEvent = allDay("allday", day, day)

        val index = DayIndex.build(listOf(timedEvent, allDayEvent))

        assertThat(index[day]).containsExactly(allDayEvent, timedEvent).inOrder()
    }
}
