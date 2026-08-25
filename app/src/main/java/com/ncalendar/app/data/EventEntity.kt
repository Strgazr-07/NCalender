package com.ncalendar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDateTime
import java.time.LocalDate

@Entity(tableName = "events")
@TypeConverters(Converters::class)
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val calendarId: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val repeat: String,
    val repeatInterval: Int = 1,
    val repeatByDays: Set<Int> = emptySet(),
    val repeatEndDate: LocalDate? = null,
    val repeatEndCount: Int? = null,
    val repeatExceptionDates: Set<LocalDate> = emptySet(),
    val reminders: List<Int>,
    val location: String?,
    val notes: String?,
)

fun EventEntity.toDomain(): EventItem {
    val cal = Calendars.get(calendarId)
    val rule = RepeatRule.fromStorage(repeat)
    return EventItem(
        id = id,
        title = title,
        calendarId = calendarId,
        start = start,
        end = end,
        allDay = allDay,
        repeat = rule,
        repeatInterval = repeatInterval.coerceAtLeast(1),
        repeatByDays = repeatByDays,
        repeatEndDate = repeatEndDate,
        repeatEndCount = repeatEndCount,
        repeatExceptionDates = repeatExceptionDates,
        reminders = reminders,
        location = location,
        notes = notes,
        color = cal.color,
        calendarName = cal.name,
        isRecurring = rule != RepeatRule.NONE,
    )
}

fun EventItem.toEntity() = EventEntity(
    id = id,
    title = title,
    calendarId = calendarId,
    start = start,
    end = end,
    allDay = allDay,
    repeat = repeat.name,
    repeatInterval = repeatInterval.coerceAtLeast(1),
    repeatByDays = repeatByDays,
    repeatEndDate = repeatEndDate,
    repeatEndCount = repeatEndCount,
    repeatExceptionDates = repeatExceptionDates,
    reminders = reminders,
    location = location,
    notes = notes,
)

class Converters {
    @TypeConverter
    fun fromLocalDateTime(v: LocalDateTime?): String? = v?.toString()

    @TypeConverter
    fun toLocalDateTime(v: String?): LocalDateTime? = v?.let(LocalDateTime::parse)

    @TypeConverter
    fun fromLocalDate(v: LocalDate?): String? = v?.toString()

    @TypeConverter
    fun toLocalDate(v: String?): LocalDate? = v?.let(LocalDate::parse)

    @TypeConverter
    fun fromIntList(v: List<Int>): String = v.joinToString(",")

    @TypeConverter
    fun toIntList(v: String): List<Int> =
        if (v.isBlank()) emptyList() else v.split(",").map { it.trim().toInt() }

    @TypeConverter
    fun fromIntSet(v: Set<Int>): String = v.sorted().joinToString(",")

    @TypeConverter
    fun toIntSet(v: String): Set<Int> =
        if (v.isBlank()) emptySet() else v.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    @TypeConverter
    fun fromLocalDateSet(v: Set<LocalDate>): String = v.sorted().joinToString(",") { it.toString() }

    @TypeConverter
    fun toLocalDateSet(v: String): Set<LocalDate> =
        if (v.isBlank()) emptySet() else v.split(",").mapNotNull { s -> runCatching { LocalDate.parse(s.trim()) }.getOrNull() }.toSet()
}
