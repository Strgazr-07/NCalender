package com.ncalendar.app.notifications

import com.ncalendar.app.data.EventItem
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a reminder alarm should actually fire. Extracted out of [ReminderScheduler] so the math
 * is unit-testable without AlarmManager or a Context — see ReminderTimingTest.
 */
object ReminderTiming {

    /** Fired-immediately alarms are pushed this far out so the alarm still registers rather
     *  than being treated as already-past by AlarmManager. */
    const val IMMEDIATE_GRACE_MS = 5_000L

    /**
     * All-day events have no clock time of their own, so "N minutes before" is meaningless for
     * them — the previous behavior computed literally N minutes before midnight, which for the
     * default 10-minute reminder meant 23:50 the night before, inside most people's Do Not
     * Disturb window and effectively invisible. Instead, the minutes are floored to whole days
     * and the alarm fires at [allDayReminderTime] that many days earlier: 0/10/30/60 → the
     * morning of, 1440 → the previous morning, 10080 → a week before.
     *
     * Timed events keep the plain minutes-before-start math. Either way, a trigger that has
     * already passed while the event itself is still ahead (e.g. an event created minutes
     * before it starts) fires right away rather than being silently dropped.
     */
    fun triggerAt(
        event: EventItem,
        minutesBefore: Int,
        allDayReminderTime: LocalTime,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val raw = if (event.allDay) {
            val daysBefore = (minutesBefore / (24 * 60)).toLong()
            event.startDate.minusDays(daysBefore).atTime(allDayReminderTime)
                .atZone(zone).toInstant().toEpochMilli()
        } else {
            event.start.atZone(zone).toInstant().toEpochMilli() - minutesBefore * 60_000L
        }
        return if (raw <= now) now + IMMEDIATE_GRACE_MS else raw
    }
}
