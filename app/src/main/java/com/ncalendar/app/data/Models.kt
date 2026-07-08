package com.ncalendar.app.data

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class RepeatRule(val label: String) {
    NONE("Never"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    WEEKDAY("Weekdays"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    /** iCal RRULE for the system calendar provider, or null for non-repeating. */
    fun toRRule(): String? = when (this) {
        NONE -> null
        DAILY -> "FREQ=DAILY"
        WEEKLY -> "FREQ=WEEKLY"
        WEEKDAY -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
        MONTHLY -> "FREQ=MONTHLY"
        YEARLY -> "FREQ=YEARLY"
    }

    companion object {
        fun fromStorage(s: String?): RepeatRule =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: NONE

        fun fromRRule(rrule: String?): RepeatRule {
            if (rrule.isNullOrBlank()) return NONE
            val r = rrule.uppercase()
            return when {
                r.contains("FREQ=DAILY") -> DAILY
                r.contains("FREQ=MONTHLY") -> MONTHLY
                r.contains("FREQ=YEARLY") -> YEARLY
                r.contains("FREQ=WEEKLY") && r.contains("BYDAY=MO,TU,WE,TH,FR") -> WEEKDAY
                r.contains("FREQ=WEEKLY") -> WEEKLY
                else -> WEEKLY
            }
        }
    }
}

/**
 * A calendar the user can view/write to. In demo mode these are the four built-ins
 * below; when the real system calendar is connected they come from CalendarContract.
 */
data class CalendarInfo(
    val id: String,
    val name: String,
    val color: Color,
    val accountName: String = "",
    val accountType: String = "",
    val isWritable: Boolean = true,
    val isPrimary: Boolean = false,
)

object Calendars {
    // The offline calendar set: everything stays on this phone, no accounts.
    val PERSONAL = CalendarInfo("p_personal", "Personal", Color(0xFFE8E8E6), "", "On this device")
    val FAMILY = CalendarInfo("p_family", "Family", Color(0xFFC2A878), "", "On this device")
    val WORK = CalendarInfo("g_work", "Work", Color(0xFF6F9DB8), "", "On this device")

    val all = listOf(PERSONAL, FAMILY, WORK)
    private val byId = all.associateBy { it.id }
    fun get(id: String): CalendarInfo = byId[id] ?: PERSONAL
}

data class EventItem(
    val id: String,
    val title: String,
    val calendarId: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val repeat: RepeatRule = RepeatRule.NONE,
    val reminders: List<Int> = emptyList(),
    val location: String? = null,
    val notes: String? = null,
    // Resolved presentation fields (from the owning calendar) so the UI never
    // needs a global calendar lookup — works for demo + real system calendars.
    val color: Color = Color(0xFFE8E8E6),
    val calendarName: String = "",
    val isRecurring: Boolean = false,
) {
    val startDate: LocalDate get() = start.toLocalDate()
    val endDate: LocalDate get() = end.toLocalDate()
}

/** Date/time labels + tiny NLU used across the app — ported from the original design's Component logic. */
object CalendarFormats {
    val DOW = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    val DOW_SHORT = arrayOf("S", "M", "T", "W", "T", "F", "S")
    val MON = arrayOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    )
    val MON_FULL = arrayOf(
        "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST",
        "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
    )

    fun pad(n: Int): String = n.toString().padStart(2, '0')
    fun fmtTime(t: LocalTime): String = "${pad(t.hour)}:${pad(t.minute)}"
    fun fmtHour(h: Int): String = "${pad(h)}:00"

    fun dowIndex(d: LocalDate): Int = d.dayOfWeek.value % 7 // Mon=1..Sun=7 -> Sun=0..Sat=6

    fun dowShortLabels(weekStart: Int): List<String> = (0 until 7).map { DOW_SHORT[(weekStart + it) % 7] }

    /** 42-cell month grid (6 weeks) starting on `weekStart` (0=Sun, 1=Mon) containing `anchor`'s month. */
    fun monthGridDates(anchor: LocalDate, weekStart: Int): List<LocalDate> {
        val first = anchor.withDayOfMonth(1)
        val off = (dowIndex(first) - weekStart + 7) % 7
        val gridStart = first.minusDays(off.toLong())
        return (0 until 42).map { gridStart.plusDays(it.toLong()) }
    }

    /** 7-day week containing `day`, starting on `weekStart`. */
    fun weekDates(day: LocalDate, weekStart: Int): List<LocalDate> {
        val off = (dowIndex(day) - weekStart + 7) % 7
        val start = day.minusDays(off.toLong())
        return (0 until 7).map { start.plusDays(it.toLong()) }
    }

    fun fmtDateShort(d: LocalDate): String = "${MON[d.monthValue - 1]} ${d.dayOfMonth}"

    fun fmtDateFull(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> {
            val w = DOW[dowIndex(d)]
            "${w[0]}${w.substring(1).lowercase()} ${MON[d.monthValue - 1]} ${d.dayOfMonth}"
        }
    }

    fun timeLabelFor(e: EventItem): String =
        if (e.allDay) "All-day" else "${fmtTime(e.start.toLocalTime())} – ${fmtTime(e.end.toLocalTime())}"

    fun reminderLabel(m: Int): String = when {
        m == 0 -> "At time of event"
        m < 60 -> "$m minutes before"
        m < 1440 -> {
            val h = m / 60
            "$h hour${if (h == 1) "" else "s"} before"
        }
        else -> {
            val d = m / 1440
            "$d day${if (d == 1) "" else "s"} before"
        }
    }

    private val workRe = Regex(
        "standup|sync|planning|review|1:1|meeting|roadmap|work|team|call w|client|sprint",
        RegexOption.IGNORE_CASE
    )
    private val familyRe = Regex("birthday|mom|dad|family|dinner|anniversary", RegexOption.IGNORE_CASE)

    fun guessCal(title: String?): String {
        val t = title ?: ""
        return when {
            workRe.containsMatchIn(t) -> Calendars.WORK.id
            familyRe.containsMatchIn(t) -> Calendars.FAMILY.id
            else -> Calendars.PERSONAL.id
        }
    }

    private val dayNames = listOf(
        "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
    )
    private val timeRe = Regex("(\\d{1,2})(:(\\d{2}))?\\s*(am|pm)", RegexOption.IGNORE_CASE)

    data class QuickParse(
        val title: String?,
        val date: LocalDate,
        val startTime: LocalTime?,
        val endTime: LocalTime?,
    )

    /** Parses free text like "Lunch with Sam Friday 1pm" into a title/date/time guess. */
    fun parseQuick(text: String, today: LocalDate): QuickParse {
        val low = text.lowercase()
        var date = today
        var foundDay: Any? = null
        dayNames.forEachIndexed { i, d -> if (low.contains(d)) foundDay = i }
        if (low.contains("tomorrow")) foundDay = "tom"
        if (low.contains("today")) foundDay = "tod"
        when (val fd = foundDay) {
            "tom" -> date = today.plusDays(1)
            "tod" -> date = today
            is Int -> {
                val todayIdx = today.dayOfWeek.value % 7
                var add = (fd - todayIdx + 7) % 7
                if (add == 0) add = 7
                date = today.plusDays(add.toLong())
            }
        }
        var startTime: LocalTime? = null
        var endTime: LocalTime? = null
        val tm = timeRe.find(low)
        if (tm != null) {
            var h = tm.groupValues[1].toInt()
            val mm = tm.groupValues[3].toIntOrNull() ?: 0
            val ampm = tm.groupValues[4]
            if (ampm.equals("pm", true) && h < 12) h += 12
            if (ampm.equals("am", true) && h == 12) h = 0
            startTime = LocalTime.of(h, mm)
            endTime = LocalTime.of((h + 1) % 24, mm)
        }
        val title = text
            .replace(Regex("\\b(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b\\d{1,2}(:\\d{2})?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bat\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return QuickParse(title.ifBlank { null }, date, startTime, endTime)
    }
}

fun hexWithAlpha(color: Color, alpha: Float): Color = color.copy(alpha = alpha)
