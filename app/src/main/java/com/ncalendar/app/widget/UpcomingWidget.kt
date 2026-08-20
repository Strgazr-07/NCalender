package com.ncalendar.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
        val palette = WidgetPalette.resolve(context)
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_upcoming)
            val svc = WidgetCollectionService.intent(context, WidgetCollectionService.SCHEME_UPCOMING, id)
            views.setRemoteAdapter(R.id.upcomingList, svc)
            views.setEmptyView(R.id.upcomingList, R.id.upcomingEmpty)
            // The container is real XML rather than a bitmap, so it needs theming here.
            views.setInt(
                R.id.upcomingRoot, "setBackgroundResource",
                if (palette.isDark) R.drawable.widget_surface_dark else R.drawable.widget_surface_light,
            )
            views.setTextColor(R.id.upcomingEmpty, palette.textDim)
            // Tapping any row opens the app (rows supply an empty fill-in intent).
            val tap = Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            views.setPendingIntentTemplate(R.id.upcomingList, PendingIntent.getActivity(context, id, tap, flags))
            appWidgetManager.updateAppWidget(id, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.upcomingList)
        }
    }

    /** Without this, resizing the widget never re-rendered its rows — they stayed at whatever
     *  size they were first drawn at. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }
}

/** Loads the upcoming events and renders each as a bitmap row on the binder thread. */
class UpcomingFactory(private val context: Context, private val appWidgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<EventItem> = emptyList()
    // Parallel to rows: true where this event begins a new date (draws the day column).
    private var firstOfDay: BooleanArray = BooleanArray(0)
    private var rowW = DEFAULT_ROW_W
    private var rowH = DEFAULT_ROW_H

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val repo = EventRepository.get(context)
        // The repo's own debounce makes this cheap when the provider path just refreshed
        // (UpcomingWidget.onUpdate triggers this right after its own refresh), which matters
        // because this runs synchronously on a binder thread.
        runBlocking { repo.refresh(LocalDate.now()) }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        rows = repo.events.value
            .filter { if (it.allDay) !it.endDate.isBefore(today) else it.end.isAfter(now) }
            .sortedWith(compareBy<EventItem> { it.start }.thenByDescending { it.allDay })
            .take(15)
        firstOfDay = BooleanArray(rows.size) { i -> i == 0 || rows[i].startDate != rows[i - 1].startDate }
        measureRow()
    }

    /**
     * Rows used to render at a fixed 440x150 regardless of the widget's real size, then get
     * scaled by the ImageView — so on a small widget the text was both shrunk by the downscale
     * AND still truncated at the width the fixed bitmap implied. Sizing the bitmap to the
     * actual widget means the ellipsis lands where the user can actually see the edge.
     */
    private fun measureRow() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val options = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }.getOrNull() ?: return
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        if (minWidthDp <= 0) return
        val density = context.resources.displayMetrics.density
        // Minus the 8dp padding on each side of the list container (widget_upcoming.xml).
        val widthPx = ((minWidthDp - 16) * density).toInt().coerceIn(160, 1600)
        rowW = widthPx
        rowH = (widthPx * ROW_ASPECT).toInt().coerceIn(80, 400)
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val e = rows[position]
        val day = if (firstOfDay.getOrElse(position) { true }) e.startDate else null
        val rv = RemoteViews(context.packageName, R.layout.widget_upcoming_row)
        rv.setImageViewBitmap(R.id.rowImage, WidgetRenderer.renderUpcomingRow(context, rowW, rowH, e, day, LocalDate.now()))
        rv.setOnClickFillInIntent(R.id.rowImage, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    // Deliberately UNSTABLE. Each item here is a rendered bitmap whose appearance depends on
    // the theme, the widget's measured size, event dots and which day is today — none of which
    // are expressible in an item id. With stable ids the launcher's RemoteViewsAdapter cache
    // (which persists across app updates) reuses the view it already has for a given id and
    // never calls getViewAt again, so cells kept displaying bitmaps rendered by an older build.
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private companion object {
        const val DEFAULT_ROW_W = 440
        const val DEFAULT_ROW_H = 150
        /** Row height as a fraction of its width — keeps the type scale steady as the widget
         *  is resized, since renderUpcomingRow sizes its text off the row height. */
        const val ROW_ASPECT = 150f / 440f
    }
}
