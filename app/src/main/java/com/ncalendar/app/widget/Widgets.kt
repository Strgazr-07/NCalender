package com.ncalendar.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import com.ncalendar.app.MainActivity
import com.ncalendar.app.R
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Shared base: query the store once, render this widget's bitmap per instance, wire a tap. */
abstract class BaseCalendarWidget : AppWidgetProvider() {

    abstract fun renderBitmap(context: Context, w: Int, h: Int, events: List<EventItem>, now: LocalDateTime): Bitmap

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = EventRepository.get(context)
                repo.refresh(LocalDate.now())
                val events = repo.events.value
                val now = LocalDateTime.now()
                for (id in appWidgetIds) {
                    val (w, h) = sizePx(context, appWidgetManager, id)
                    val bmp = renderBitmap(context, w, h, events, now)
                    val views = RemoteViews(context.packageName, R.layout.widget_image)
                    views.setImageViewBitmap(R.id.widgetImage, bmp)
                    views.setOnClickPendingIntent(R.id.widgetImage, openApp(context, id))
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun openApp(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun sizePx(context: Context, manager: AppWidgetManager, id: Int): Pair<Int, Int> {
        val opts = manager.getAppWidgetOptions(id)
        val density = context.resources.displayMetrics.density
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val w = (minW * density).toInt().coerceIn(160, 1600)
        val h = (minH * density).toInt().coerceIn(90, 1600)
        return w to h
    }
}

class NextEventWidget : BaseCalendarWidget() {
    override fun renderBitmap(context: Context, w: Int, h: Int, events: List<EventItem>, now: LocalDateTime): Bitmap {
        // Ongoing event first (shown as "now"), else the next upcoming one.
        val timed = events.filterNot { it.allDay }
        val next = timed.filter { !it.start.isAfter(now) && it.end.isAfter(now) }.minByOrNull { it.start }
            ?: timed.filter { it.start.isAfter(now) }.minByOrNull { it.start }
        return WidgetRenderer.renderNextEvent(context, w, h, next, now)
    }
}

class DotDateWidget : BaseCalendarWidget() {
    override fun renderBitmap(context: Context, w: Int, h: Int, events: List<EventItem>, now: LocalDateTime): Bitmap =
        WidgetRenderer.renderDotDate(context, w, h)
}

class AgendaWidget : BaseCalendarWidget() {
    override fun renderBitmap(context: Context, w: Int, h: Int, events: List<EventItem>, now: LocalDateTime): Bitmap {
        val today = LocalDate.now()
        val todays = events.filter { !it.startDate.isAfter(today) && !it.endDate.isBefore(today) }
            .sortedWith(compareByDescending<EventItem> { it.allDay }.thenBy { it.start })
        return WidgetRenderer.renderAgenda(context, w, h, todays)
    }
}

class MiniMonthWidget : BaseCalendarWidget() {
    override fun renderBitmap(context: Context, w: Int, h: Int, events: List<EventItem>, now: LocalDateTime): Bitmap {
        val eventDays = events.flatMap {
            generateSequence(it.startDate) { d -> if (d.isBefore(it.endDate)) d.plusDays(1) else null }.toList()
        }.toSet()
        return WidgetRenderer.renderMiniMonth(context, w, h, eventDays)
    }
}

/** Refresh every placed NCalendar widget after the event store changes. */
object AppWidgets {
    private val providers = listOf(
        NextEventWidget::class.java,
        DotDateWidget::class.java,
        AgendaWidget::class.java,
        MiniMonthWidget::class.java,
    )

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        providers.forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
