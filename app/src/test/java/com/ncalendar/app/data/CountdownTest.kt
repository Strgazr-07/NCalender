package com.ncalendar.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class CountdownTest {

    private val now = LocalDateTime.of(2026, 8, 20, 10, 0)

    @Test
    fun theReportedEightHundredHourCaseNowReadsAsADate() {
        // The exact bug from the screenshot: 49,244 minutes out rendered as "in 820h 44m".
        val target = now.plusMinutes(49_244)

        val result = CalendarFormats.countdown(now, target)

        assertThat(result).doesNotContain("820")
        assertThat(result).doesNotContain("h")
        assertThat(result).isEqualTo(CalendarFormats.fmtDateShort(target.toLocalDate()))
    }

    @Test
    fun elevenPmToNineAmNextMorningIsTomorrowNotTenHours() {
        // Bucketing on elapsed duration would call this "in 10h"; bucketing on calendar day
        // (the rule this function is built around) correctly calls it tomorrow.
        val lateNight = LocalDateTime.of(2026, 8, 20, 23, 0)
        val nextMorning = LocalDateTime.of(2026, 8, 21, 9, 0)

        assertThat(CalendarFormats.countdown(lateNight, nextMorning)).isEqualTo("tomorrow")
    }

    @Test
    fun minutesWithinTheHour() {
        assertThat(CalendarFormats.countdown(now, now.plusMinutes(12))).isEqualTo("in 12 min")
    }

    @Test
    fun hoursAndMinutesLaterSameDay() {
        assertThat(CalendarFormats.countdown(now, now.plusMinutes(200))).isEqualTo("in 3h 20m")
    }

    @Test
    fun minutesAreDroppedWellIntoTheDay() {
        // 7h20m out — the trailing minutes stop being useful information at that distance.
        assertThat(CalendarFormats.countdown(now, now.plusMinutes(440))).isEqualTo("in 7h")
    }

    @Test
    fun wholeHoursOmitTheMinuteComponent() {
        assertThat(CalendarFormats.countdown(now, now.plusHours(3))).isEqualTo("in 3h")
    }

    @Test
    fun daysWithinTheWeek() {
        assertThat(CalendarFormats.countdown(now, now.plusDays(3))).isEqualTo("in 3 days")
    }

    @Test
    fun weeksUpToAMonth() {
        assertThat(CalendarFormats.countdown(now, now.plusDays(7))).isEqualTo("in 1 week")
        assertThat(CalendarFormats.countdown(now, now.plusDays(15))).isEqualTo("in 2 weeks")
    }

    @Test
    fun beyondFourWeeksBecomesADate() {
        val target = now.plusDays(40)
        assertThat(CalendarFormats.countdown(now, target)).isEqualTo(CalendarFormats.fmtDateShort(target.toLocalDate()))
    }

    @Test
    fun aDateInAnotherYearCarriesTheYear() {
        val target = LocalDateTime.of(2027, 3, 14, 9, 0)
        assertThat(CalendarFormats.countdown(now, target)).isEqualTo("MAR 14, 2027")
    }

    @Test
    fun pastOrPresentIsNow() {
        assertThat(CalendarFormats.countdown(now, now)).isEqualTo("now")
        assertThat(CalendarFormats.countdown(now, now.minusHours(2))).isEqualTo("now")
    }

    @Test
    fun compactFormIsTerse() {
        assertThat(CalendarFormats.countdown(now, now.plusMinutes(12), compact = true)).isEqualTo("12m")
        assertThat(CalendarFormats.countdown(now, now.plusHours(3), compact = true)).isEqualTo("3h")
        assertThat(CalendarFormats.countdown(now, now.plusDays(3), compact = true)).isEqualTo("3d")
        assertThat(CalendarFormats.countdown(now, now.plusDays(15), compact = true)).isEqualTo("2w")
    }

    @Test
    fun remainingReplacesTheUnboundedMinutesLeftForm() {
        // Was "480 min left" for an 8-hour event.
        assertThat(CalendarFormats.remaining(now, now.plusMinutes(480))).isEqualTo("8h left")
        assertThat(CalendarFormats.remaining(now, now.plusMinutes(20))).isEqualTo("20 min left")
    }

    @Test
    fun remainingHandlesMultiDayEvents() {
        assertThat(CalendarFormats.remaining(now, now.plusDays(1))).isEqualTo("ends tomorrow")
        val target = now.plusDays(4)
        assertThat(CalendarFormats.remaining(now, target)).isEqualTo("ends ${CalendarFormats.fmtDateShort(target.toLocalDate())}")
    }
}
