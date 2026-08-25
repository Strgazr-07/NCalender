package com.ncalendar.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ncalendar.app.data.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/**
 * Keeps the Next Event widget's countdown fresh in the background. A widget bitmap is frozen at
 * the moment it's drawn — without this, "in 12 min" only advances on the OS's own periodic
 * update, which the platform floors at 30 minutes regardless of what the manifest requests, so
 * a countdown could sit visibly stale for most of an event.
 *
 * Self-reschedules at an interval that tightens as the relevant event gets closer, rather than
 * ticking at a fixed rate forever — a flat 1-minute alarm would be needless battery drain for a
 * widget that spends most of the day not showing anything urgent.
 */
object WidgetTicker {
    const val ACTION_TICK = "com.ncalendar.app.ACTION_WIDGET_TICK"
    private const val REQUEST_CODE = 0x60000003

    /** Re-evaluates how soon the next countdown-relevant moment is and arms exactly one alarm
     *  for it (or none, if nothing currently needs sub-30-minute freshness). Cheap enough to
     *  call from every existing AppWidgets.refreshAll call site — see that function. */
    fun sync(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancel(context, alarmManager)

        // No point ticking for a widget nobody has placed.
        val manager = AppWidgetManager.getInstance(context) ?: return
        val placed = manager.getAppWidgetIds(ComponentName(context, NextEventWidget::class.java)).isNotEmpty()
        if (!placed) return

        val now = LocalDateTime.now()
        val timed = EventRepository.get(context).events.value.filterNot { it.allDay }
        val ongoing = timed.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) }
        val next = timed.filter { it.start.isAfter(now) }.minByOrNull { it.start }

        val delayMs = when {
            ongoing != null -> 5 * 60_000L
            next == null -> return // nothing to count down to
            else -> {
                val minsAway = Duration.between(now, next.start).toMinutes()
                when {
                    minsAway <= 60 -> 60_000L
                    minsAway <= 24 * 60 -> 15 * 60_000L
                    else -> return // far enough out that the 30-min OS period is plenty
                }
            }
        }

        val intent = Intent(context, WidgetTickReceiver::class.java).apply { action = ACTION_TICK }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + delayMs
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancel(context: Context, alarmManager: AlarmManager) {
        val intent = Intent(context, WidgetTickReceiver::class.java).apply { action = ACTION_TICK }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetTicker.ACTION_TICK) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Repaints every widget (cheap — bitmap render off cached event data) and,
                // via the hook inside it, re-syncs the next tick's interval for the new "now".
                AppWidgets.refreshAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
