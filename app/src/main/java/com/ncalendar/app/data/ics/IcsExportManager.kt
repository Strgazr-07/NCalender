package com.ncalendar.app.data.ics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ncalendar.app.data.EventItem
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object IcsExportManager {
    private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFmt = DateTimeFormatter.BASIC_ISO_DATE

    fun shareEvents(context: Context, events: List<EventItem>, fileName: String) {
        val file = File(context.cacheDir, fileName).apply { writeText(toIcs(events), Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share calendar"))
    }

    fun toIcs(events: List<EventItem>): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//NCalendar//Android//EN")
        for (e in events) appendEvent(e)
        appendLine("END:VCALENDAR")
    }

    private fun StringBuilder.appendEvent(e: EventItem) {
        appendLine("BEGIN:VEVENT")
        appendLine("UID:${escape(e.id)}@ncalendar")
        appendLine("SUMMARY:${escape(e.title)}")
        if (e.allDay) {
            appendLine("DTSTART;VALUE=DATE:${e.startDate.format(dateFmt)}")
            appendLine("DTEND;VALUE=DATE:${e.endDate.plusDays(1).format(dateFmt)}")
        } else {
            val zone = ZoneId.systemDefault()
            appendLine("DTSTART;TZID=${zone.id}:${e.start.format(dateTimeFmt)}")
            appendLine("DTEND;TZID=${zone.id}:${e.end.format(dateTimeFmt)}")
        }
        e.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
        e.notes?.takeIf { it.isNotBlank() }?.let { appendLine("DESCRIPTION:${escape(it)}") }
        e.repeat.toRRule(e.repeatInterval, e.repeatByDays, e.repeatEndDate, e.repeatEndCount)
            ?.let { appendLine("RRULE:$it") }
        appendLine("END:VEVENT")
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
