package com.ncalendar.app.data

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class RepeatRule(val label: String) {
    NONE("Never"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    WEEKDAY("Weekdays"),
    MONTHLY("Monthly"),
    YEARLY("Each year");

    companion object {
        fun fromStorage(s: String?): RepeatRule =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: NONE

        fun fromRRule(rrule: String?): RepeatRule = parseRRule(rrule).rule

        fun parseRRule(rrule: String?): RepeatConfig {
            if (rrule.isNullOrBlank()) return RepeatConfig()
            val parts = rrule.split(';')
                .mapNotNull {
                    val idx = it.indexOf('=')
                    if (idx <= 0) null else it.substring(0, idx).uppercase() to it.substring(idx + 1)
                }
                .toMap()
            val byDays = parts["BYDAY"].orEmpty()
                .split(',')
                .mapNotNull { rRuleDayToIso(it.takeLast(2).uppercase()) }
                .toSet()
            val freq = parts["FREQ"]?.uppercase()
            val rule = when (freq) {
                "DAILY" -> DAILY
                "MONTHLY" -> MONTHLY
                "YEARLY" -> YEARLY
                "WEEKLY" -> if (byDays == setOf(1, 2, 3, 4, 5)) WEEKDAY else WEEKLY
                else -> WEEKLY
            }
            val until = parts["UNTIL"]?.take(8)?.let { raw ->
                runCatching {
                    LocalDate.of(raw.substring(0, 4).toInt(), raw.substring(4, 6).toInt(), raw.substring(6, 8).toInt())
                }.getOrNull()
            }
            return RepeatConfig(
                rule = rule,
                interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                byDays = if (rule == WEEKDAY) emptySet() else byDays,
                until = until,
                count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 },
            )
        }
    }
}

data class RepeatConfig(
    val rule: RepeatRule = RepeatRule.NONE,
    val interval: Int = 1,
    val byDays: Set<Int> = emptySet(),
    val until: LocalDate? = null,
    val count: Int? = null,
)

fun isoDayToRRule(day: Int): String = when (day) {
    1 -> "MO"
    2 -> "TU"
    3 -> "WE"
    4 -> "TH"
    5 -> "FR"
    6 -> "SA"
    7 -> "SU"
    else -> "MO"
}

fun rRuleDayToIso(day: String): Int? = when (day.uppercase()) {
    "MO" -> 1
    "TU" -> 2
    "WE" -> 3
    "TH" -> 4
    "FR" -> 5
    "SA" -> 6
    "SU" -> 7
    else -> null
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
    /** A regional public-holiday calendar (Google's "Holidays in X", or similarly named on
     *  other providers) — see CalendarProvider.isHolidayCalendar. Lets "Show holidays" be a
     *  single Settings toggle instead of requiring the user to find and hide the exact
     *  holiday calendar by hand in Manage calendars. */
    val isHoliday: Boolean = false,
)

/** Matches the exact grouping CalendarsScreen displays accounts under (accountName, falling
 *  back to "On this device") — the account-level visibility toggle must correspond 1:1 with
 *  the visual group the user sees, not some other derived key. */
val CalendarInfo.accountKey: String get() = accountName.ifBlank { "On this device" }

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
    val repeatInterval: Int = 1,
    val repeatByDays: Set<Int> = emptySet(),
    val repeatEndDate: LocalDate? = null,
    val repeatEndCount: Int? = null,
    // Local-only ("edit/delete just this occurrence") series exceptions: dates the base series
    // should NOT expand into an instance for, because either a standalone replacement event
    // exists for that date or the occurrence was deleted outright. Only meaningful on the raw
    // base entity Recurrence.expand reads; irrelevant (and unused) on an already-expanded
    // instance. System-calendar events use CalendarContract's own exception mechanism instead —
    // see CalendarProvider.insertExceptionEdit/insertExceptionCancel.
    val repeatExceptionDates: Set<LocalDate> = emptySet(),
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

/** The recurring SERIES' own DTSTART/duration, as read from CalendarContract directly (not
 *  recurrence-expanded). See CalendarProvider.queryEventBase. */
data class EventBase(
    val start: LocalDateTime,
    val durationMinutes: Long,
    val allDay: Boolean,
    val rrule: String?,
)

/** How much of a recurring series an edit/delete/drag applies to. THIS_AND_FUTURE is
 *  deliberately not offered: it needs UNTIL-splitting the original series and re-homing any
 *  later exceptions, which is a lot of provider surgery for comparatively little payoff over
 *  just these two. */
enum class EditScope { THIS_EVENT, ALL_EVENTS }

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

    /**
     * How far off [to] is, in the largest unit that still reads naturally. The load-bearing
     * rule is that everything past today buckets by CALENDAR DAY, not by elapsed duration —
     * bucketing on `duration / 1440` renders "tomorrow at 9am, seen from 11pm tonight" as
     * "in 10h", and (the reported bug) leaves hours accumulating without limit until a
     * five-week-away event reads "in 820h 44m". Only same-day events fall through to hours.
     *
     * [compact] trims the words for tight spaces (widget corners): "3d" rather than "in 3 days".
     */
    fun countdown(from: LocalDateTime, to: LocalDateTime, compact: Boolean = false): String {
        val minutes = java.time.Duration.between(from, to).toMinutes()
        if (minutes <= 0) return "now"
        val days = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate())
        return when {
            days == 0L && minutes < 60 -> if (compact) "${minutes}m" else "in $minutes min"
            days == 0L -> {
                val h = minutes / 60
                val m = minutes % 60
                // Minutes stop earning their place past a few hours out.
                if (compact) "${h}h" else if (h >= 6 || m == 0L) "in ${h}h" else "in ${h}h ${m}m"
            }
            days == 1L -> if (compact) "1d" else "tomorrow"
            days < 7L -> if (compact) "${days}d" else "in $days days"
            days < 28L -> {
                val weeks = days / 7
                if (compact) "${weeks}w" else if (weeks == 1L) "in 1 week" else "in $weeks weeks"
            }
            to.year != from.year -> "${fmtDateShort(to.toLocalDate())}, ${to.year}"
            else -> fmtDateShort(to.toLocalDate())
        }
    }

    /** How much of an in-progress event is left. Same bucketing as [countdown], phrased as a
     *  remainder rather than a wait. */
    fun remaining(now: LocalDateTime, end: LocalDateTime, compact: Boolean = false): String {
        val minutes = java.time.Duration.between(now, end).toMinutes()
        if (minutes <= 0) return "ending"
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), end.toLocalDate())
        return when {
            days == 0L && minutes < 60 -> if (compact) "${minutes}m" else "$minutes min left"
            days == 0L -> {
                val h = minutes / 60
                val m = minutes % 60
                if (compact) "${h}h" else if (h >= 6 || m == 0L) "${h}h left" else "${h}h ${m}m left"
            }
            days == 1L -> if (compact) "1d" else "ends tomorrow"
            else -> if (compact) "${days}d" else "ends ${fmtDateShort(end.toLocalDate())}"
        }
    }

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

    fun repeatSummary(
        rule: RepeatRule,
        interval: Int,
        byDays: Set<Int>,
        until: LocalDate?,
        count: Int?,
    ): String {
        if (rule == RepeatRule.NONE) return RepeatRule.NONE.label
        val every = interval.coerceAtLeast(1)
        val unit = when (rule) {
            RepeatRule.DAILY -> "day"
            RepeatRule.WEEKLY, RepeatRule.WEEKDAY -> "week"
            RepeatRule.MONTHLY -> "month"
            RepeatRule.YEARLY -> "year"
            RepeatRule.NONE -> "day"
        }
        val base = if (every == 1) rule.label else "Every $every ${unit}s"
        val days = when {
            rule == RepeatRule.WEEKDAY -> " on weekdays"
            rule == RepeatRule.WEEKLY && byDays.isNotEmpty() ->
                " on " + byDays.sorted().joinToString(", ") { DOW[(it % 7)].lowercase().replaceFirstChar(Char::uppercase) }
            else -> ""
        }
        val end = when {
            until != null -> " · until ${fmtDateShort(until)}"
            count != null -> " · $count times"
            else -> ""
        }
        return "$base$days$end"
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
