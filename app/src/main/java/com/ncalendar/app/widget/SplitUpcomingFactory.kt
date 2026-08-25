package com.ncalendar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.ncalendar.app.MainActivity
import com.ncalendar.app.R
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.EventRepository
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime

/** The Split widget's right-side scrollable event list — the same per-row bitmap renderer the
 *  standalone Upcoming widget uses (renderUpcomingRow), just sized to the narrower column
 *  Split actually gives it instead of the full widget width. */
class SplitUpcomingFactory(private val context: Context, private val appWidgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<EventItem> = emptyList()
    // Parallel to rows: true where this event begins a new date (draws the day column).
    private var firstOfDay: BooleanArray = BooleanArray(0)
    private var rowW = 220
    private var rowH = 64

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val repo = EventRepository.get(context)
        runBlocking { repo.refresh(LocalDate.now()) }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        rows = repo.events.value
            .filter { if (it.allDay) !it.endDate.isBefore(today) else it.end.isAfter(now) }
            .sortedWith(compareBy<EventItem> { it.start }.thenByDescending { it.allDay })
            .take(20)
        firstOfDay = BooleanArray(rows.size) { i -> i == 0 || rows[i].startDate != rows[i - 1].startDate }
        measureRow()
    }

    /** MUST track widget_split.xml's right column's weight (0.48) and padding, or rows render
     *  sized for space they don't actually have. */
    private fun measureRow() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val options = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }.getOrNull() ?: return
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        if (minWidthDp <= 0) return
        val density = context.resources.displayMetrics.density
        val listWidthDp = minWidthDp * (1f - MonthCalendarWidget.SPLIT_LEFT_FRACTION) - 16f
        rowW = (listWidthDp * density).toInt().coerceIn(120, 900)
        rowH = (rowW * 0.42f).toInt().coerceIn(56, 220)
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val e = rows[position]
        val day = if (firstOfDay.getOrElse(position) { true }) e.startDate else null
        val rv = RemoteViews(context.packageName, R.layout.widget_upcoming_row)
        rv.setImageViewBitmap(R.id.rowImage, WidgetRenderer.renderUpcomingRow(context, rowW, rowH, e, day, LocalDate.now()))
        rv.setOnClickFillInIntent(R.id.rowImage, Intent().putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, e.id))
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
}
