package com.ncalendar.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * RFC 5545 requires an RRULE's UNTIL value type to match its DTSTART's: a bare DATE for an
 * all-day (DATE) series, a UTC DATE-TIME for a timed (DATE-TIME) series. The previous
 * implementation always emitted a UTC date-time UNTIL, which is invalid for an all-day series
 * and — for a device behind UTC — silently drops the final occurrence of a timed series (the
 * UTC instant lands before that day's local occurrence time).
 */
class RRuleTest {

    @Test
    fun allDaySeriesUntilIsABareDate() {
        val rrule = RRule.of(
            rule = RepeatRule.WEEKLY,
            until = LocalDate.of(2026, 8, 21),
            allDay = true,
        )

        // A bare DATE value: 8 digits, no time component, no trailing Z — unlike the
        // DATE-TIME form below. Extracted as its own clause since "UNTIL" itself contains
        // a "T", which would make a raw substring check on the whole rule meaningless.
        val untilClause = rrule!!.substringAfter("UNTIL=").substringBefore(";")
        assertThat(untilClause).isEqualTo("20260821")
    }

    @Test
    fun timedSeriesUntilIsAUtcDateTimeCoveringTheFullLocalDay() {
        // A device behind UTC (Los Angeles, UTC-7 in August under PDT): the last local day's final moment
        // (23:59:59 local) is still the *same* UTC calendar day, so the last occurrence
        // must not be dropped.
        val rrule = RRule.of(
            rule = RepeatRule.DAILY,
            until = LocalDate.of(2026, 8, 21),
            allDay = false,
            zone = ZoneId.of("America/Los_Angeles"),
        )

        // 2026-08-21T23:59:59-07:00 == 2026-08-22T06:59:59Z
        assertThat(rrule).contains("UNTIL=20260822T065959Z")
    }

    @Test
    fun timedSeriesUntilAheadOfUtcRolledBackCorrectly() {
        // A device ahead of UTC (Kolkata, +05:30): 23:59:59 local on the until-date is
        // still the same UTC day for this offset.
        val rrule = RRule.of(
            rule = RepeatRule.DAILY,
            until = LocalDate.of(2026, 8, 21),
            allDay = false,
            zone = ZoneId.of("Asia/Kolkata"),
        )

        // 2026-08-21T23:59:59+05:30 == 2026-08-21T18:29:59Z
        assertThat(rrule).contains("UNTIL=20260821T182959Z")
    }

    @Test
    fun roundTripsThroughParseRRuleForAllDaySeries() {
        val original = RRule.of(
            rule = RepeatRule.WEEKLY,
            interval = 2,
            byDays = setOf(1, 3, 5),
            until = LocalDate.of(2027, 1, 1),
            allDay = true,
        )

        val parsed = RepeatRule.parseRRule(original)

        assertThat(parsed.rule).isEqualTo(RepeatRule.WEEKLY)
        assertThat(parsed.interval).isEqualTo(2)
        assertThat(parsed.byDays).containsExactly(1, 3, 5)
        assertThat(parsed.until).isEqualTo(LocalDate.of(2027, 1, 1))
    }

    @Test
    fun nonRepeatingRuleProducesNoRRule() {
        assertThat(RRule.of(RepeatRule.NONE, until = LocalDate.of(2026, 1, 1))).isNull()
    }

    @Test
    fun countIsCarriedThrough() {
        val rrule = RRule.of(rule = RepeatRule.DAILY, count = 5)
        assertThat(rrule).contains("COUNT=5")
    }
}
