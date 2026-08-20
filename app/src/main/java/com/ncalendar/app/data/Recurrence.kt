package com.ncalendar.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Expands a repeating base event into concrete instances within a date window. */
object Recurrence {

    /** Instance ids are "<baseId>::<yyyy-mm-dd>"; strip the suffix to edit/delete the series. */
    fun baseId(instanceId: String): String = instanceId.substringBefore("::")

    /** For a local-only instance id "<baseId>::<yyyy-mm-dd>", the ORIGINAL occurrence date
     *  (before any edit) — used to record an exception on the base series. Null for the
     *  series' own first occurrence (which keeps the bare base id) or a non-instance id. */
    fun instanceDate(instanceId: String): LocalDate? {
        val suffix = instanceId.substringAfter("::", missingDelimiterValue = "")
        if (suffix.isBlank()) return null
        return runCatching { LocalDate.parse(suffix) }.getOrNull()
    }

    fun expand(base: EventItem, windowStart: LocalDate, windowEnd: LocalDate): List<EventItem> {
        if (base.repeat == RepeatRule.NONE) return listOf(base)

        val durationMin = ChronoUnit.MINUTES.between(base.start, base.end).coerceAtLeast(0)
        val startTime = base.start.toLocalTime()
        val out = ArrayList<EventItem>()
        // COUNT must be counted against the series' true start, not the load window — jumping
        // straight to windowStart silently drops every occurrence before it from the count, so
        // a "5 times" series could still be emitting instances well past its 5th. An UNTIL-only
        // or unbounded series has no count to get right, so it can skip ahead to the window.
        val countBounded = base.repeatEndCount != null
        var d = if (!countBounded && base.startDate.isBefore(windowStart)) windowStart else base.startDate
        var guard = 0
        var emitted = 0
        val lastAllowed = base.repeatEndDate?.let { minOf(it, windowEnd) } ?: windowEnd
        // A count-bounded walk from the true start can run far longer than the window itself
        // when the series began years ago, so it gets more headroom than the plain day-by-day
        // guard (the UI caps COUNT at 99, but a rare YEARLY series still needs ~99 years of days).
        val guardLimit = if (countBounded) 8000 else 2000
        while (!d.isAfter(lastAllowed) && guard < guardLimit) {
            guard++
            if (!d.isBefore(base.startDate) && matches(base.repeat, base.startDate, d, base.repeatInterval, base.repeatByDays)) {
                emitted++
                // An excepted date still counts toward COUNT (it's occurrence #N, just
                // edited-out or cancelled) — it just doesn't get emitted here. A standalone
                // replacement event, if any, is a separate non-recurring row already in the
                // store and shows up on its own.
                val isException = d in base.repeatExceptionDates
                if (!isException && (base.repeatEndCount == null || emitted <= base.repeatEndCount)) {
                    if (!d.isBefore(windowStart)) {
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
