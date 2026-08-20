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
import com.ncalendar.app.data.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires at reminder time (ACTION_REMIND) and posts the event notification with a Snooze
 * action; ACTION_SNOOZE re-arms the same notification 10 minutes later. ACTION_DAILY_SYNC is a
 * self-perpetuating once-a-day wakeup (see ReminderScheduler.armDailySync) that keeps the
 * alarm horizon rolling forward even when the app process otherwise never runs.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> snooze(context, intent)
            ACTION_DAILY_SYNC -> dailySync(context)
            ACTION_LIVE_START -> liveStart(context, intent)
            ACTION_LIVE_END -> liveEnd(context, intent)
            ACTION_LIVE_DISMISS -> LiveEventNotifier.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            else -> notify(context, intent)
        }
    }

    /** The live-countdown chip's lead time has arrived: post it, then re-run the scheduler so
     *  the NEXT event's chip gets armed — the chain is self-perpetuating rather than polled. */
    private fun liveStart(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = EventRepository.get(appContext)
                repo.refresh()
                val event = repo.events.value.find { it.id == eventId }
                if (event != null) {
                    LiveEventNotifier.show(appContext, event, ReminderScheduler.liveNotificationId(eventId))
                }
                ReminderScheduler.syncAll(appContext, repo.events.value)
            } finally {
                pending.finish()
            }
        }
    }

    /** The event has started: the chip's chronometer would otherwise keep ticking past zero
     *  forever with no further app involvement (Chronometer is a pure display, not an alert),
     *  so this stops it cleanly and re-syncs to arm whatever's next. */
    private fun liveEnd(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        LiveEventNotifier.cancel(context, ReminderScheduler.liveNotificationId(eventId))
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = EventRepository.get(appContext)
                repo.refresh()
                ReminderScheduler.syncAll(appContext, repo.events.value)
            } finally {
                pending.finish()
            }
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
            context, ReminderScheduler.snoozeRequestCode(notificationId), snoozeIntent,
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
        // Its own request-code region (see ReminderScheduler's partitioning): reusing the
        // original alarm's code would let the next reminder sync cancel the snoozed alarm
        // before it fires.
        val pending = PendingIntent.getBroadcast(
            context, ReminderScheduler.rearmRequestCode(notificationId), fireIntent,
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

    private fun dailySync(context: Context) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = EventRepository.get(appContext)
                repo.refresh(force = true)
                ReminderScheduler.syncAll(appContext, repo.events.value)
            } finally {
                ReminderScheduler.armDailySync(appContext) // re-arm for tomorrow
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.ncalendar.app.ACTION_REMIND"
        const val ACTION_SNOOZE = "com.ncalendar.app.ACTION_SNOOZE"
        const val ACTION_DAILY_SYNC = "com.ncalendar.app.ACTION_DAILY_SYNC"
        const val ACTION_LIVE_START = "com.ncalendar.app.ACTION_LIVE_START"
        const val ACTION_LIVE_END = "com.ncalendar.app.ACTION_LIVE_END"
        const val ACTION_LIVE_DISMISS = "com.ncalendar.app.ACTION_LIVE_DISMISS"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_WHEN = "when"
    }
}
