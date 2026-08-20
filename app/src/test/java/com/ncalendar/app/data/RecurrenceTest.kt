package com.ncalendar.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceTest {

    private fun series(
        start: LocalDate,
        rule: RepeatRule,
        interval: Int = 1,
        byDays: Set<Int> = emptySet(),
        count: Int? = null,
        until: LocalDate? = null,
        exceptions: Set<LocalDate> = emptySet(),
    ) = EventItem(
        id = "base",
        title = "t",
        calendarId = "1",
        start = LocalDateTime.of(start, LocalTime.of(9, 0)),
        end = LocalDateTime.of(start, LocalTime.of(10, 0)),
        allDay = false,
        repeat = rule,
        repeatInterval = interval,
        repeatByDays = byDays,
        repeatEndCount = count,
        repeatEndDate = until,
        repeatExceptionDates = exceptions,
    )

    @Test
    fun exceptionDateIsSkippedButStillCountsTowardCount() {
        // "Edit/delete just this occurrence" (Phase 2): the base series must skip an excepted
        // date entirely (a standalone replacement event, if any, is a separate non-recurring
        // row elsewhere in the store) — but COUNT is still consumed by it, matching how a
        // cancelled/modified RFC 5545 exception doesn't shrink the series' total.
        val start = LocalDate.of(2026, 1, 1)
        val excepted = start.plusDays(2) // the 3rd daily occurrence
        val base = series(start, RepeatRule.DAILY, count = 5, exceptions = setOf(excepted))

        val instances = Recurrence.expand(base, start, start.plusDays(30))

        assertThat(instances.map { it.startDate }).containsExactly(
            start, start.plusDays(1), start.plusDays(3), start.plusDays(4),
        ).inOrder()
        assertThat(instances.map { it.startDate }).doesNotContain(excepted)
    }

    @Test
    fun instanceDateParsesTheDatedSuffix() {
        assertThat(Recurrence.instanceDate("base123::2026-08-21")).isEqualTo(LocalDate.of(2026, 8, 21))
    }

    @Test
    fun instanceDateIsNullForTheSeriesOwnFirstOccurrence() {
        // The first occurrence keeps the bare base id (no "::" suffix) — see Recurrence.expand.
        assertThat(Recurrence.instanceDate("base123")).isNull()
    }

    @Test
    fun countIsAppliedFromTheTrueSeriesStartEvenWhenBeforeTheWindow() {
        // Series starts well before the load window and repeats 5 times total. Only
        // occurrences 4 and 5 fall inside the window — the earlier bug counted "emitted"
        // from windowStart, so it thought none had happened yet and kept emitting past 5.
        val start = LocalDate.of(2026, 1, 1)
        val base = series(start, RepeatRule.DAILY, count = 5)
        val windowStart = start.plusDays(3) // occurrences 1-3 already happened before this
        val windowEnd = start.plusDays(30)

        val instances = Recurrence.expand(base, windowStart, windowEnd)

        // Only the 4th (Jan 4) and 5th (Jan 5) occurrences are both in-count and in-window.
        assertThat(instances.map { it.startDate }).containsExactly(
            start.plusDays(3), start.plusDays(4),
        ).inOrder()
    }

    @Test
    fun countExhaustsCorrectlyWhenSeriesStartsInsideTheWindow() {
        val start = LocalDate.of(2026, 1, 1)
        val base = series(start, RepeatRule.DAILY, count = 3)

        val instances = Recurrence.expand(base, start, start.plusDays(30))

        assertThat(instances).hasSize(3)
        assertThat(instances.last().startDate).isEqualTo(start.plusDays(2))
    }

    @Test
    fun weeklyByDayAcrossADaylightSavingTransitionStillHitsEveryChosenWeekday() {
        // Recurrence operates purely on LocalDate, so a DST transition (US: 2026-03-08)
        // shouldn't skip or double a day — pinning that here.
        val start = LocalDate.of(2026, 3, 2) // a Monday
        val base = series(start, RepeatRule.WEEKLY, byDays = setOf(1, 3)) // Mon, Wed

        val instances = Recurrence.expand(base, start, start.plusDays(14)) // through Mon Mar 16, inclusive

        assertThat(instances.map { it.startDate }).containsExactly(
            LocalDate.of(2026, 3, 2), // Mon
            LocalDate.of(2026, 3, 4), // Wed
            LocalDate.of(2026, 3, 9), // Mon (crosses the Mar 8 DST change)
            LocalDate.of(2026, 3, 11), // Wed
            LocalDate.of(2026, 3, 16), // Mon
        ).inOrder()
    }

    @Test
    fun yearlyOnFeb29OnlyOccursInLeapYears() {
        // Documenting current behavior, not asserting it's the only valid choice: a Feb 29
        // anchor simply has no matching day in a non-leap year, so that year is skipped
        // rather than substituted to Feb 28 or Mar 1.
        val anchor = LocalDate.of(2028, 2, 29) // 2028 is a leap year
        val base = series(anchor, RepeatRule.YEARLY)

        val instances = Recurrence.expand(base, anchor, LocalDate.of(2033, 1, 1))

        // 2028 (anchor) and 2032 are leap years; 2029-2031 and 2033 are not.
        assertThat(instances.map { it.startDate.year }).containsExactly(2028, 2032).inOrder()
    }

    @Test
    fun monthlyOnThe31stSkipsShorterMonths() {
        // Documenting current behavior: MONTHLY requires an exact dayOfMonth match, so a
        // 31st-anchored series silently skips every 30-day-or-shorter month rather than
        // rolling to the last day of the month. Pre-existing behavior, out of scope to fix
        // here, pinned so a future change to this is a deliberate one.
        val anchor = LocalDate.of(2026, 1, 31)
        val base = series(anchor, RepeatRule.MONTHLY)

        val instances = Recurrence.expand(base, anchor, LocalDate.of(2026, 5, 1))

        // Jan 31 and Mar 31 exist; Feb, Apr (and the walk stopping before May 31) don't.
        assertThat(instances.map { it.startDate }).containsExactly(
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31),
        ).inOrder()
    }

    @Test
    fun nonRepeatingEventIsReturnedAsItself() {
        val start = LocalDate.of(2026, 6, 1)
        val base = series(start, RepeatRule.NONE)

        val instances = Recurrence.expand(base, start.minusDays(5), start.plusDays(5))

        assertThat(instances).containsExactly(base)
    }
}
