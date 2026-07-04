package com.ncalendar.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import java.time.ZoneId

/**
 * Schedules exact alarms for each event reminder so notifications fire even when
 * the app isn't running. Keeps the set of scheduled events in prefs so alarms
 * for deleted events can be cancelled on the next sync.
 */
object ReminderScheduler {

    private const val PREFS = "ncalendar_reminders"
    private const val KEY_KNOWN_IDS = "known_event_ids"
    private const val MAX_SLOTS = 8

    /** Only alarms within this window are registered; refreshes roll it forward. */
    private const val HORIZON_DAYS = 60L

    fun syncAll(context: Context, events: List<EventItem>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previouslyKnown = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()) ?: emptySet()

        // Only events that can actually fire soon get an alarm — scheduling all
        // ~2 years of recurring instances would blow the per-app alarm budget
        // and burn main-thread time in thousands of PendingIntent lookups.
        val now = System.currentTimeMillis()
        val horizonEnd = now + HORIZON_DAYS * 24 * 60 * 60 * 1000
        val candidates = events.filter { e ->
            if (e.reminders.isEmpty()) return@filter false
            val startMillis = e.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            startMillis in (now + 1)..horizonEnd
        }
        val currentIds = candidates.map { it.id }.toSet()

        // Cancel alarms for events that no longer exist or left the window.
        (previouslyKnown - currentIds).forEach { cancelEvent(context, it) }

        candidates.forEach { scheduleEvent(context, it) }

        prefs.edit().putStringSet(KEY_KNOWN_IDS, currentIds).apply()
    }

    fun scheduleEvent(context: Context, event: EventItem) {
        cancelEvent(context, event)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()

        val startMillis = event.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        event.reminders.take(MAX_SLOTS).forEachIndexed { slot, minutesBefore ->
            var triggerAt = startMillis - minutesBefore * 60_000L
            // Event already started — nothing to remind about.
            if (startMillis <= now) return@forEachIndexed
            // Reminder time already passed but the event is still ahead (e.g. the
            // event was created minutes before it starts) — fire right away
            // instead of silently dropping the notification.
            if (triggerAt <= now) triggerAt = now + 5_000L

            val pending = pendingIntent(context, event, slot, minutesBefore, create = true) ?: return@forEachIndexed
            scheduleExact(alarmManager, triggerAt, pending)
        }
    }

    fun cancelEvent(context: Context, event: EventItem) = cancelEvent(context, event.id)

    private fun cancelEvent(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        for (slot in 0 until MAX_SLOTS) {
            val requestCode = requestCode(eventId, slot)
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_REMIND
            }
            val pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun scheduleExact(alarmManager: AlarmManager, triggerAt: Long, pending: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(
        context: Context,
        event: EventItem,
        slot: Int,
        minutesBefore: Int,
        create: Boolean,
    ): PendingIntent? {
        val requestCode = requestCode(event.id, slot)
        val text = buildString {
            append(CalendarFormats.timeLabelFor(event))
            if (!event.location.isNullOrBlank()) append(" · ${event.location}")
        }
        val calName = event.calendarName.ifBlank { "Event" }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, "${event.title} · $calName")
            putExtra(ReminderReceiver.EXTRA_TEXT, text)
            putExtra(ReminderReceiver.EXTRA_WHEN, event.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun requestCode(eventId: String, slot: Int): Int = ("$eventId#$slot").hashCode()
}
