package com.ncalendar.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "ncalendar_reminders"
    private const val CHANNEL_NAME = "Event reminders"
    private const val CHANNEL_DESC = "Reminders for your calendar events"

    /** Separate from the reminders channel on purpose: the live countdown is an ongoing,
     *  silent, badge-less notification, and users need to be able to turn it off without
     *  losing their actual reminders (or have it buzz at IMPORTANCE_HIGH every time it
     *  re-posts). */
    const val LIVE_CHANNEL_ID = "ncalendar_live"
    private const val LIVE_CHANNEL_NAME = "Live countdown"
    private const val LIVE_CHANNEL_DESC = "An ongoing countdown to your next event"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
            )
        }
        if (manager.getNotificationChannel(LIVE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(LIVE_CHANNEL_ID, LIVE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = LIVE_CHANNEL_DESC
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }

    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
