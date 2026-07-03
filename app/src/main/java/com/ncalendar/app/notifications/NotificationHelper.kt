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

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
