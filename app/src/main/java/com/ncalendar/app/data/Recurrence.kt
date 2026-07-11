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
        var emitted = 0
        val lastAllowed = base.repeatEndDate?.let { minOf(it, windowEnd) } ?: windowEnd
        while (!d.isAfter(lastAllowed) && guard < 2000) {
            guard++
            if (!d.isBefore(base.startDate) && matches(base.repeat, base.startDate, d, base.repeatInterval, base.repeatByDays)) {
                emitted++
                if (base.repeatEndCount == null || emitted <= base.repeatEndCount) {
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
                if (base.repeatEndCount != null && emitted >= base.repeatEndCount) break
            }
            d = d.plusDays(1)
        }
        return out
    }

    fun matches(rule: RepeatRule, anchor: LocalDate, day: LocalDate, interval: Int, byDays: Set<Int>): Boolean = when (rule) {
        RepeatRule.DAILY -> ChronoUnit.DAYS.between(anchor, day) % interval.coerceAtLeast(1) == 0L
        RepeatRule.WEEKLY -> {
            val weeks = ChronoUnit.WEEKS.between(anchor.with(DayOfWeek.MONDAY), day.with(DayOfWeek.MONDAY))
            weeks % interval.coerceAtLeast(1) == 0L && day.dayOfWeek.value in byDays.ifEmpty { setOf(anchor.dayOfWeek.value) }
        }
        RepeatRule.WEEKDAY -> {
            val weeks = ChronoUnit.WEEKS.between(anchor.with(DayOfWeek.MONDAY), day.with(DayOfWeek.MONDAY))
            weeks % interval.coerceAtLeast(1) == 0L && day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
        }
        RepeatRule.MONTHLY -> {
            val months = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), day.withDayOfMonth(1))
            months % interval.coerceAtLeast(1) == 0L && day.dayOfMonth == anchor.dayOfMonth
        }
        RepeatRule.YEARLY -> {
            val years = ChronoUnit.YEARS.between(anchor.withDayOfYear(1), day.withDayOfYear(1))
            years % interval.coerceAtLeast(1) == 0L && day.dayOfMonth == anchor.dayOfMonth && day.month == anchor.month
        }
        RepeatRule.NONE -> day == anchor
    }
}
