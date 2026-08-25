package com.ncalendar.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure date/time <-> CalendarContract-millis conversions, extracted out of [CalendarProvider]
 * so they're unit-testable without a ContentResolver. All-day events are stored at UTC midnight
 * per the CalendarContract contract with an EXCLUSIVE end (UTC midnight of the day after the
 * inclusive last day); timed events use the device zone. Both the write side (toUtcMidnightMillis/
 * toMillis/endMillis) and the read side (allDayStartDate/allDayEndDate/timedDateTime) must stay
 * symmetric in the zone they use — an asymmetry here previously caused all-day events to render
 * a day early for any device ahead of UTC (fixed in 4330c7c; see AllDayTimezoneTest).
 */
object CalendarTimes {

    fun toMillis(dt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dt.atZone(zone).toInstant().toEpochMilli()

    /** All-day DTSTART/BEGIN must be expressed at UTC midnight per CalendarContract. */
    fun toUtcMidnightMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    /** All-day DTEND/END is exclusive: UTC midnight of the day AFTER the inclusive last day. */
    fun allDayEndMillis(inclusiveEndDate: LocalDate): Long =
        toUtcMidnightMillis(inclusiveEndDate.plusDays(1))

    /** DTEND in millis for either kind of event. */
    fun endMillis(event: EventItem, zone: ZoneId = ZoneId.systemDefault()): Long =
        if (event.allDay) allDayEndMillis(event.end.toLocalDate()) else toMillis(event.end, zone)

    fun durationString(event: EventItem): String =
        if (event.allDay) {
            // All-day durations are whole days (end date is inclusive here).
            val days = ChronoUnit.DAYS.between(event.start.toLocalDate(), event.end.toLocalDate()) + 1
            "P${days.coerceAtLeast(1)}D"
        } else {
            val minutes = ChronoUnit.MINUTES.between(event.start, event.end).coerceAtLeast(0)
            "PT${minutes}M"
        }

    /** Reads an all-day DTSTART/BEGIN (UTC midnight) back to the calendar LocalDate. */
    fun allDayStartDate(beginMillis: Long): LocalDate =
        Instant.ofEpochMilli(beginMillis).atZone(ZoneId.of("UTC")).toLocalDate()

    /** Reads an all-day DTEND/END (exclusive UTC midnight) back to the inclusive last LocalDate. */
    fun allDayEndDate(startDate: LocalDate, endMillisExclusive: Long): LocalDate {
        val lastDay = Instant.ofEpochMilli(endMillisExclusive).atZone(ZoneId.of("UTC")).toLocalDate().minusDays(1)
        return maxOf(startDate, lastDay)
    }

    /** Reads a timed BEGIN/END back to a device-zone LocalDateTime. */
    fun timedDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    /** Parses a CalendarContract DURATION string ("P3D", "PT90M", "PT1H", "PT30S") to minutes. */
    fun parseDurationMinutes(duration: String?, allDay: Boolean): Long {
        if (duration.isNullOrBlank()) return if (allDay) 24 * 60L else 60L
        if (allDay) {
            val days = Regex("P(\\d+)D").find(duration)?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(1) ?: 1L
            return days * 24 * 60
        }
        Regex("PT(\\d+)S").find(duration)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it / 60 }
        Regex("PT(\\d+)M").find(duration)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it }
        Regex("PT(\\d+)H").find(duration)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it * 60 }
        return 60L
    }
}
