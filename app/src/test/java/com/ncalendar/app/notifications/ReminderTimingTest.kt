package com.ncalendar.app.notifications

import com.google.common.truth.Truth.assertThat
import com.ncalendar.app.data.EventItem
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimingTest {

    private val zone = ZoneId.of("UTC")
    private val nineAm = LocalTime.of(9, 0)

    private fun millis(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    private fun timed(start: LocalDateTime) = EventItem(
        id = "t", title = "t", calendarId = "1",
        start = start, end = start.plusHours(1), allDay = false,
    )

    private fun allDay(date: LocalDate, endDate: LocalDate = date) = EventItem(
        id = "a", title = "a", calendarId = "1",
        start = date.atStartOfDay(), end = endDate.atStartOfDay(), allDay = true,
    )

    @Test
    fun timedEventFiresTheGivenMinutesBeforeStart() {
        val start = LocalDateTime.of(2026, 8, 21, 14, 0)
        val now = millis(LocalDateTime.of(2026, 8, 20, 0, 0))

        val trigger = ReminderTiming.triggerAt(timed(start), 30, nineAm, now, zone)

        assertThat(trigger).isEqualTo(millis(LocalDateTime.of(2026, 8, 21, 13, 30)))
    }

    @Test
    fun allDayEventWithAShortReminderFiresTheMorningOfNotTheNightBefore() {
        // The headline bug: 10 minutes before an all-day event's midnight start put the alarm
        // at 23:50 the previous night, inside most people's Do Not Disturb window. Anything
        // under a day now floors to 0 days and fires at the configured morning time instead.
        val date = LocalDate.of(2026, 8, 21)
        val now = millis(LocalDateTime.of(2026, 8, 1, 0, 0))

        val trigger = ReminderTiming.triggerAt(allDay(date), 10, nineAm, now, zone)

        assertThat(trigger).isEqualTo(millis(LocalDateTime.of(2026, 8, 21, 9, 0)))
    }

    @Test
    fun allDayEventOneDayBeforeFiresThePreviousMorning() {
        val date = LocalDate.of(2026, 8, 21)
        val now = millis(LocalDateTime.of(2026, 8, 1, 0, 0))

        val trigger = ReminderTiming.triggerAt(allDay(date), 1440, nineAm, now, zone)

        assertThat(trigger).isEqualTo(millis(LocalDateTime.of(2026, 8, 20, 9, 0)))
    }

    @Test
    fun allDayEventOneWeekBeforeFiresSevenMorningsEarlier() {
        val date = LocalDate.of(2026, 8, 21)
        val now = millis(LocalDateTime.of(2026, 8, 1, 0, 0))

        val trigger = ReminderTiming.triggerAt(allDay(date), 10080, nineAm, now, zone)

        assertThat(trigger).isEqualTo(millis(LocalDateTime.of(2026, 8, 14, 9, 0)))
    }

    @Test
    fun allDayReminderHonorsAConfiguredNonDefaultTime() {
        val date = LocalDate.of(2026, 8, 21)
        val now = millis(LocalDateTime.of(2026, 8, 1, 0, 0))

        val trigger = ReminderTiming.triggerAt(allDay(date), 0, LocalTime.of(7, 30), now, zone)

        assertThat(trigger).isEqualTo(millis(LocalDateTime.of(2026, 8, 21, 7, 30)))
    }

    @Test
    fun alreadyPassedTriggerForAStillUpcomingEventFiresImmediately() {
        // An event created a few minutes before it starts: the nominal "30 minutes before" is
        // already in the past, but the reminder is still worth firing rather than dropping.
        val start = LocalDateTime.of(2026, 8, 21, 14, 0)
        val now = millis(LocalDateTime.of(2026, 8, 21, 13, 50))

        val trigger = ReminderTiming.triggerAt(timed(start), 30, nineAm, now, zone)

        assertThat(trigger).isEqualTo(now + ReminderTiming.IMMEDIATE_GRACE_MS)
    }

    @Test
    fun futureTriggerIsNotPerturbedByTheImmediateGrace() {
        val start = LocalDateTime.of(2026, 8, 21, 14, 0)
        val now = millis(LocalDateTime.of(2026, 8, 21, 13, 0))

        val trigger = ReminderTiming.triggerAt(timed(start), 30, nineAm, now, zone)

        assertThat(trigger).isEqualTo(millis(LocalDateTime.of(2026, 8, 21, 13, 30)))
    }
}
