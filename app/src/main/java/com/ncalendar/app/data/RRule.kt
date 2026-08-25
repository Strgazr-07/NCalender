package com.ncalendar.app.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds an iCal RRULE string for the system calendar provider. Extracted off the [RepeatRule]
 * enum because a correct UNTIL needs to know whether the series is all-day: RFC 5545 requires
 * UNTIL's value type to match DTSTART's — a bare DATE for an all-day series, a UTC DATE-TIME for
 * a timed one — and for a timed series, the device zone the "end of day" boundary is measured
 * in. Writing a UTC date-time UNTIL on an all-day series (the previous behavior) either drops or
 * extends the last occurrence for any device that isn't UTC, and technically produces an invalid
 * RRULE for an all-day (DATE) DTSTART. See RRuleTest.
 */
object RRule {

    fun of(
        rule: RepeatRule,
        interval: Int = 1,
        byDays: Set<Int> = emptySet(),
        until: LocalDate? = null,
        count: Int? = null,
        allDay: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (rule == RepeatRule.NONE) return null
        return buildList {
            add("FREQ=${freqName(rule)}")
            if (interval > 1) add("INTERVAL=${interval.coerceAtLeast(1)}")
            val days = when {
                rule == RepeatRule.WEEKDAY -> setOf(1, 2, 3, 4, 5)
                byDays.isNotEmpty() -> byDays
                else -> emptySet()
            }
            if (days.isNotEmpty()) add("BYDAY=${days.sorted().joinToString(",") { isoDayToRRule(it) }}")
            until?.let { add("UNTIL=${untilValue(it, allDay, zone)}") }
            count?.takeIf { it > 0 }?.let { add("COUNT=$it") }
        }.joinToString(";")
    }

    private fun untilValue(until: LocalDate, allDay: Boolean, zone: ZoneId): String =
        if (allDay) {
            "${until.year}${pad2(until.monthValue)}${pad2(until.dayOfMonth)}"
        } else {
            // End-of-day in the device zone, converted to UTC — matches DTSTART's own
            // write path (CalendarTimes.toMillis uses the device zone for timed events).
            val endOfDayUtc = until.atTime(23, 59, 59).atZone(zone)
                .withZoneSameInstant(ZoneId.of("UTC"))
            "${endOfDayUtc.year}${pad2(endOfDayUtc.monthValue)}${pad2(endOfDayUtc.dayOfMonth)}" +
                "T${pad2(endOfDayUtc.hour)}${pad2(endOfDayUtc.minute)}${pad2(endOfDayUtc.second)}Z"
        }

    private fun freqName(rule: RepeatRule): String = when (rule) {
        RepeatRule.NONE -> ""
        RepeatRule.DAILY -> "DAILY"
        RepeatRule.WEEKLY, RepeatRule.WEEKDAY -> "WEEKLY"
        RepeatRule.MONTHLY -> "MONTHLY"
        RepeatRule.YEARLY -> "YEARLY"
    }

    private fun pad2(n: Int): String = n.toString().padStart(2, '0')
}
