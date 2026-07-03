package com.ncalendar.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ncalendar.app.MainActivity
import com.ncalendar.app.R

/**
 * Fires at reminder time (ACTION_REMIND) and posts the event notification with a
 * Snooze action; ACTION_SNOOZE re-arms the same notification 10 minutes later.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> snooze(context, intent)
            else -> notify(context, intent)
        }
    }

    private fun notify(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming event"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""

        NotificationHelper.ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, eventId)
        }
        val contentIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtras(intent)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, notificationId + 1_000_000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Snooze 10 min", snoozePending)
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private fun snooze(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        NotificationManagerCompat.from(context).cancel(notificationId)

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val fireIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtras(intent)
            setAction(ACTION_REMIND)
        }
        val pending = PendingIntent.getBroadcast(
            context, notificationId, fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + 10 * 60 * 1000L
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.ncalendar.app.ACTION_REMIND"
        const val ACTION_SNOOZE = "com.ncalendar.app.ACTION_SNOOZE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_WHEN = "when"
    }
}
