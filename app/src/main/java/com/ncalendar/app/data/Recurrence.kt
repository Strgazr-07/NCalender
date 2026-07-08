package com.ncalendar.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Expands a repeating base event into concrete instances within a date window. */
object Recurrence {

    /** Instance ids are "<baseId>::<yyyy-mm-dd>"; strip the suffix to edit/delete the series. */
    fun baseId(instanceId: String): String = instanceId.substringBefore("::")

    fun expand(base: EventItem, windowStart: LocalDate, windowEnd: LocalDate): List<EventItem> {
        if (base.repeat == RepeatRule.NONE) return listOf(base)

        val durationMin = ChronoUnit.MINUTES.between(base.start, base.end).coerceAtLeast(0)
        val startTime = base.start.toLocalTime()
        val out = ArrayList<EventItem>()
        var d = if (base.startDate.isBefore(windowStart)) windowStart else base.startDate
        var guard = 0
        while (!d.isAfter(windowEnd) && guard < 1000) {
            guard++
            if (!d.isBefore(base.startDate) && matches(base.repeat, base.startDate, d)) {
                val start = LocalDateTime.of(d, startTime)
                val isFirst = d == base.startDate
                out.add(
                    base.copy(
                        id = if (isFirst) base.id else "${base.id}::$d",
                        start = start,
                        end = start.plusMinutes(durationMin),
                        isRecurring = true,
                    )
                )
            }
            d = d.plusDays(1)
        }
        return out
    }

    private fun matches(rule: RepeatRule, anchor: LocalDate, day: LocalDate): Boolean = when (rule) {
        RepeatRule.DAILY -> true
        RepeatRule.WEEKLY -> day.dayOfWeek == anchor.dayOfWeek
        RepeatRule.WEEKDAY -> day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
        RepeatRule.MONTHLY -> day.dayOfMonth == anchor.dayOfMonth
        RepeatRule.YEARLY -> day.dayOfMonth == anchor.dayOfMonth && day.month == anchor.month
        RepeatRule.NONE -> day == anchor
    }
}
