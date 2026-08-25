package com.ncalendar.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate
import java.util.TimeZone

/**
 * Regression guard for commit 4330c7c: an all-day event's start/end date must round-trip
 * through CalendarContract's write/read helpers to the exact same LocalDate(s) regardless of
 * the device's default timezone.
 *
 * The bug this locks in: the original CalendarProvider wrote all-day DTSTART using the device
 * zone (`event.start.toMillis()`, i.e. ZoneId.systemDefault()) but read Instances.BEGIN back
 * assuming UTC. For a device ahead of UTC (e.g. Asia/Kolkata, +05:30), midnight-local is still
 * the previous day in UTC, so the read side rendered every all-day event one day earlier than
 * created — exactly "create it on the 21st, it shows on the 20th." Devices behind UTC were
 * unaffected, which is why the bug didn't reproduce for every reporter.
 *
 * The fix (and the reason this test can be zone-independent by construction) is that the write
 * side no longer touches the device zone at all for all-day events — CalendarTimes.toUtcMidnightMillis
 * always writes UTC midnight directly. Parameterizing over TimeZone.setDefault() proves that.
 */
@RunWith(Parameterized::class)
class AllDayTimezoneTest(private val zoneId: String) {

    private lateinit var previousDefault: TimeZone

    @Before
    fun setUp() {
        previousDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previousDefault)
    }

    @Test
    fun singleDayAllDayEventRoundTrips() {
        val date = LocalDate.of(2026, 8, 21)
        val event = allDayEvent(date, date)

        val writtenStart = CalendarTimes.toUtcMidnightMillis(event.start.toLocalDate())
        val writtenEnd = CalendarTimes.endMillis(event)

        val readStart = CalendarTimes.allDayStartDate(writtenStart)
        val readEnd = CalendarTimes.allDayEndDate(readStart, writtenEnd)

        assertThat(readStart).isEqualTo(date)
        assertThat(readEnd).isEqualTo(date)
    }

    @Test
    fun multiDayAllDayEventKeepsItsExactSpan() {
        val start = LocalDate.of(2026, 12, 24)
        val end = LocalDate.of(2026, 12, 26)
        val event = allDayEvent(start, end)

        val writtenStart = CalendarTimes.toUtcMidnightMillis(event.start.toLocalDate())
        val writtenEnd = CalendarTimes.endMillis(event)

        val readStart = CalendarTimes.allDayStartDate(writtenStart)
        val readEnd = CalendarTimes.allDayEndDate(readStart, writtenEnd)

        assertThat(readStart).isEqualTo(start)
        assertThat(readEnd).isEqualTo(end)
    }

    @Test
    fun monthBoundaryAllDayEventRoundTrips() {
        // The date the original bug report used, one day before month-end — a case a
        // UTC-vs-local shift would push into the previous month, not just the previous day.
        val date = LocalDate.of(2026, 8, 31)
        val event = allDayEvent(date, date)

        val writtenStart = CalendarTimes.toUtcMidnightMillis(event.start.toLocalDate())
        val readStart = CalendarTimes.allDayStartDate(writtenStart)

        assertThat(readStart).isEqualTo(date)
    }

    private fun allDayEvent(start: LocalDate, end: LocalDate) = EventItem(
        id = "1",
        title = "test",
        calendarId = "1",
        start = start.atStartOfDay(),
        end = end.atStartOfDay(),
        allDay = true,
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun zones() = listOf(
            "Pacific/Kiritimati", // UTC+14 — furthest ahead of UTC
            "Asia/Kathmandu", // UTC+05:45 — odd half-hour offset
            "Asia/Kolkata", // UTC+05:30 — the zone that surfaced this bug
            "UTC",
            "America/Los_Angeles", // UTC-08:00 / -07:00 DST
            "Pacific/Niue", // UTC-11 — furthest behind UTC
        )
    }
}
