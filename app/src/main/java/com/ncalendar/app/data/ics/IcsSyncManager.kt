package com.ncalendar.app.data.ics

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import biweekly.Biweekly
import biweekly.ICalVersion
import biweekly.component.VEvent
import biweekly.io.WriteContext
import biweekly.io.scribe.property.RecurrenceRuleScribe
import biweekly.util.ICalDate
import com.ncalendar.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * Fetches subscribed feeds, parses them with biweekly, and mirrors the events into
 * each subscription's local calendar. All-day events use the same UTC-midnight /
 * exclusive-end semantics as user-created events so they don't smear across days.
 */
object IcsSyncManager {

    /** Sync every subscription; persists and returns the updated list. */
    suspend fun syncAll(context: Context): List<IcsSubscription> = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val updated = prefs.icsSubscriptions.map { syncOneInternal(context, it) }
        prefs.icsSubscriptions = updated
        updated
    }

    /** Sync a single subscription by id; persists and returns the full updated list. */
    suspend fun syncOne(context: Context, id: String): List<IcsSubscription> = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val updated = prefs.icsSubscriptions.map { if (it.id == id) syncOneInternal(context, it) else it }
        prefs.icsSubscriptions = updated
        updated
    }

    private fun syncOneInternal(context: Context, sub: IcsSubscription): IcsSubscription = try {
        val calId = SubscriptionCalendars.ensureCalendar(context, sub)
            ?: return sub.copy(lastError = "Couldn't create local calendar (grant calendar access)")
        val text = fetch(sub.url)
        val values = parseEvents(text)
        SubscriptionCalendars.replaceEvents(context, calId, values)
        sub.copy(calendarId = calId, lastSyncEpoch = System.currentTimeMillis(), lastError = null)
    } catch (e: Exception) {
        sub.copy(lastError = (e.message ?: e.javaClass.simpleName).take(160))
    }

    // ---------------- fetch ----------------

    private fun fetch(url: String): String {
        var current = IcsSubscription.normalizeUrl(url)
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/calendar, */*")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("User-Agent", "NCalendar/1.2 (Android)")
            }
            val code = conn.responseCode
            // Some hosts bounce webcal→https or http→https across schemes, which
            // HttpURLConnection won't auto-follow, so chase Location ourselves.
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("Redirect without Location")
                current = loc
                redirects++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Server returned HTTP $code")
            }
            val raw: InputStream = conn.inputStream
            val stream = if (conn.contentEncoding?.contains("gzip", true) == true) GZIPInputStream(raw) else raw
            return stream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    // ---------------- parse + map ----------------

    fun parseEvents(icsText: String): List<ContentValues> {
        val ical = Biweekly.parse(icsText).first() ?: return emptyList()
        val tzId = TimeZone.getDefault().id
        val writeCtx = WriteContext(ICalVersion.V2_0, ical.timezoneInfo, null)
        val out = ArrayList<ContentValues>()
        for (ve in ical.events) {
            // Skip a single bad event rather than failing the whole feed.
            runCatching { mapEvent(ve, tzId, writeCtx) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun mapEvent(ve: VEvent, tzId: String, writeCtx: WriteContext): ContentValues? {
        val dtStart = ve.dateStart?.value ?: return null
        val allDay = !dtStart.hasTime()
        val title = ve.summary?.value?.takeIf { it.isNotBlank() } ?: "(No title)"
        val rruleText = ve.recurrenceRule?.let {
            runCatching { RecurrenceRuleScribe().writeText(it, writeCtx) }.getOrNull()
        }

        val v = ContentValues()
        v.put(CalendarContract.Events.TITLE, title)
        ve.location?.value?.let { v.put(CalendarContract.Events.EVENT_LOCATION, it) }
        ve.description?.value?.let { v.put(CalendarContract.Events.DESCRIPTION, it) }
        v.put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)

        if (allDay) {
            val startDate = dtStart.toAllDayLocalDate()
            val lastDay = allDayInclusiveEnd(ve, startDate)
            v.put(CalendarContract.Events.DTSTART, utcMidnight(startDate))
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            if (rruleText != null) {
                v.put(CalendarContract.Events.RRULE, rruleText)
                val days = (ChronoUnit.DAYS.between(startDate, lastDay) + 1).coerceAtLeast(1)
                v.put(CalendarContract.Events.DURATION, "P${days}D")
            } else {
                v.put(CalendarContract.Events.DTEND, utcMidnight(lastDay.plusDays(1)))
                v.put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
            }
        } else {
            val startMillis = dtStart.time
            val endMillis = timedEndMillis(ve, startMillis)
            v.put(CalendarContract.Events.DTSTART, startMillis)
            v.put(CalendarContract.Events.EVENT_TIMEZONE, tzId)
            if (rruleText != null) {
                v.put(CalendarContract.Events.RRULE, rruleText)
                val secs = ((endMillis - startMillis) / 1000).coerceAtLeast(0)
                v.put(CalendarContract.Events.DURATION, "PT${secs}S")
            } else {
                v.put(CalendarContract.Events.DTEND, endMillis)
                v.put(CalendarContract.Events.EVENT_END_TIMEZONE, tzId)
            }
        }
        return v
    }

    /** Literal calendar date of a VALUE=DATE start, independent of any timezone. */
    private fun ICalDate.toAllDayLocalDate(): LocalDate {
        rawComponents?.let { rc ->
            runCatching { return LocalDate.of(rc.year, rc.month, rc.date) }
        }
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun allDayInclusiveEnd(ve: VEvent, startDate: LocalDate): LocalDate {
        ve.dateEnd?.value?.let { end ->
            // DTEND is exclusive for all-day events.
            val d = end.toAllDayLocalDate().minusDays(1)
            return if (d.isBefore(startDate)) startDate else d
        }
        ve.duration?.value?.let { d ->
            val days = (d.weeks ?: 0) * 7 + (d.days ?: 0)
            if (days > 0) return startDate.plusDays((days - 1).toLong())
        }
        return startDate
    }

    private fun timedEndMillis(ve: VEvent, startMillis: Long): Long {
        ve.dateEnd?.value?.let { return it.time }
        ve.duration?.value?.let { d ->
            val millis = (((((d.weeks ?: 0).toLong() * 7 + (d.days ?: 0)) * 24 +
                (d.hours ?: 0)) * 60 + (d.minutes ?: 0)) * 60 + (d.seconds ?: 0)) * 1000L
            if (millis > 0) return startMillis + millis
        }
        return startMillis + 3_600_000L // default to a 1-hour event
    }

    private fun utcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
}
