package com.ncalendar.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.ncalendar.app.MainActivity
import com.ncalendar.app.R
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.EventRepository
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A compact 2x2 that lists upcoming events (name + time window) in a scrollable list.
 * Each row is a bitmap so the Nothing fonts survive — RemoteViews can't set them.
 */
class UpcomingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_upcoming)
            val svc = Intent(context, UpcomingRemoteViewsService::class.java).apply {
                // Unique data per widget id so the host keeps separate adapters.
                data = Uri.fromParts("ncalendar", id.toString(), null)
            }
            views.setRemoteAdapter(R.id.upcomingList, svc)
            views.setEmptyView(R.id.upcomingList, R.id.upcomingEmpty)
            // Tapping any row opens the app (rows supply an empty fill-in intent).
            val tap = Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            views.setPendingIntentTemplate(R.id.upcomingList, PendingIntent.getActivity(context, id, tap, flags))
            appWidgetManager.updateAppWidget(id, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.upcomingList)
        }
    }
}

class UpcomingRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = UpcomingFactory(applicationContext)
}

/** Loads the upcoming events and renders each as a bitmap row on the binder thread. */
class UpcomingFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<EventItem> = emptyList()
    // Parallel to rows: true where this event begins a new date (draws the day column).
    private var firstOfDay: BooleanArray = BooleanArray(0)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val repo = EventRepository.get(context)
        runBlocking { repo.refresh(LocalDate.now()) }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        rows = repo.events.value
            .filter { if (it.allDay) !it.endDate.isBefore(today) else it.end.isAfter(now) }
            .sortedWith(compareBy<EventItem> { it.start }.thenByDescending { it.allDay })
            .take(15)
        firstOfDay = BooleanArray(rows.size) { i -> i == 0 || rows[i].startDate != rows[i - 1].startDate }
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val e = rows[position]
        val day = if (firstOfDay.getOrElse(position) { true }) e.startDate else null
        val rv = RemoteViews(context.packageName, R.layout.widget_upcoming_row)
        rv.setImageViewBitmap(R.id.rowImage, WidgetRenderer.renderUpcomingRow(context, 440, 150, e, day, LocalDate.now()))
        rv.setOnClickFillInIntent(R.id.rowImage, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
