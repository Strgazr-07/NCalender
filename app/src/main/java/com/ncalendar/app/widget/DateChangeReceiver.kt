package com.ncalendar.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Repaints every widget when the calendar day, timezone, or clock changes. Widgets otherwise
 * only refresh on their 30-minute period, so the date widget could sit showing yesterday for
 * up to half an hour after midnight, and the "today" highlight in the month grids with it.
 *
 * These three actions are still deliverable to a manifest-declared receiver (unlike
 * CONFIGURATION_CHANGED, which is implicit-broadcast restricted from API 26 — theme changes
 * are handled from MainActivity instead).
 */
class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> AppWidgets.refreshAll(context.applicationContext)
        }
    }
}
