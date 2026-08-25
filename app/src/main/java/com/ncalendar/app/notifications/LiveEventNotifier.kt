package com.ncalendar.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ncalendar.app.MainActivity
import com.ncalendar.app.R
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem

/**
 * The ongoing "next event" countdown — Nothing OS surfaces a promoted ongoing notification as a
 * live activity chip.
 *
 * Deliberately NOT gated entirely behind API 36: the countdown itself is a plain chronometer
 * (`setUsesChronometer` + `setChronometerCountDown` + `setWhen`), which the system ticks for
 * free all the way back to minSdk 26 with no re-posting. Only the *promotion* to a live chip
 * needs API 36, so older devices still get a useful ongoing notification rather than nothing.
 */
object LiveEventNotifier {

    /** Only events starting within this window get a live countdown — a chip counting down
     *  from six hours out is noise, not information. */
    const val LEAD_MINUTES = 60L

    fun show(context: Context, event: EventItem, notificationId: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationHelper.ensureChannel(context)

        val startMillis = event.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, event.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_LIVE_DISMISS
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, notificationId, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, NotificationHelper.LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(event.title)
            .setContentText(CalendarFormats.timeLabelFor(event))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(startMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(contentIntent)
            // Mandatory, not optional: setOngoing(true) makes this un-swipeable, so without an
            // explicit action there is no way for the user to get rid of it.
            .addAction(0, "Dismiss", dismissPending)
            // Asks Android 16+ to promote this to a live-activity chip. NotificationCompat
            // no-ops it below API 36, where the chronometer above still works on its own.
            .setRequestPromotedOngoing(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
