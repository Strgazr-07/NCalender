package com.ncalendar.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.ncalendar.app.MainActivity
import com.ncalendar.app.R
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.EventRepository
import com.ncalendar.app.data.Prefs
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
                // Covers first placement / resize — every OTHER path that changes what the
                // countdown should say already routes through AppWidgets.refreshAll below.
                WidgetTicker.sync(context)
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

/**
 * Shared base for the calendar widgets (MiniMonth, Split).
 *
 * The calendar is drawn as a SINGLE bitmap and the 42 day cells are transparent views layered
 * over it (widget_month_overlay.xml). The previous design made the grid a GridView backed by a
 * RemoteViewsFactory, which cost far more than it bought: each cell had to guess the size the
 * GridView would allocate it (the day numbers were mis-sized for months as a result), the
 * launcher cached cell views by item id and kept serving stale ones across app updates, and a
 * second collection adapter in the same widget could get crossed with the upcoming list's.
 * Drawing once and overlaying taps has none of those failure modes and is simpler to reason
 * about: what you compute is exactly what is drawn.
 */
abstract class MonthCalendarWidget : AppWidgetProvider() {
    /** 1.0 where the calendar is the whole widget (MiniMonth); Split overrides it because the
     *  calendar only occupies its left column. */
    protected open val calendarWidthFraction: Float = 1f

    protected abstract val contentLayout: Int
    protected abstract val calendarViewId: Int

    /** The layout's root, so its background can follow the theme. The XML has to name SOME
     *  background and picks the dark one; without overriding it here the widget stayed dark in
     *  light mode even though everything drawn inside it had already switched. A -night
     *  resource qualifier would be wrong: these follow the app's own theme setting, which can
     *  be LIGHT while the system is dark. */
    protected abstract val rootViewId: Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                val repo = EventRepository.get(context)
                repo.refresh(today)
                // Which days get a dot. Multi-day events mark every day they span.
                val eventDays = repo.events.value.flatMap { e ->
                    generateSequence(e.startDate) { d -> if (d.isBefore(e.endDate)) d.plusDays(1) else null }.toList()
                }.toSet()
                val palette = WidgetPalette.resolve(context)
                val cells = CalendarFormats.monthGridDates(today, Prefs(context).weekStart)
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, contentLayout)
                    views.setInt(
                        rootViewId, "setBackgroundResource",
                        if (palette.isDark) R.drawable.widget_surface_dark else R.drawable.widget_surface_light,
                    )
                    val (fullW, fullH) = sizePx(context, appWidgetManager, id)
                    val calendarW = (fullW * calendarWidthFraction).toInt().coerceAtLeast(1)
                    views.setImageViewBitmap(
                        calendarViewId,
                        WidgetRenderer.renderMonthCalendar(context, calendarW, fullH, eventDays, today),
                    )
                    bindDayTaps(context, views, id, cells, today)
                    onCustomize(context, views, id, palette, fullW, fullH)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                WidgetTicker.sync(context)
                pending.finish()
            }
        }
    }

    /** Points each overlay cell at its own date. Cells outside the current month are drawn
     *  blank by the renderer, so they deliberately get no tap target — a fresh RemoteViews is
     *  built every update, so simply not setting one leaves them inert. */
    private fun bindDayTaps(context: Context, views: RemoteViews, appWidgetId: Int, cells: List<LocalDate>, month: LocalDate) {
        cells.forEachIndexed { index, date ->
            if (date.monthValue != month.monthValue || date.year != month.year) return@forEachIndexed
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DATE, date.toString())
                // Intent.filterEquals — which is what decides whether two PendingIntents are
                // the same — ignores extras entirely. Without a distinct Uri per cell all 42
                // would collapse onto one PendingIntent and every date would open the same day.
                data = Uri.parse("ncalendar://day/$appWidgetId/$date")
            }
            views.setOnClickPendingIntent(
                DAY_IDS[index],
                PendingIntent.getActivity(
                    context, DAY_REQUEST_BASE + appWidgetId * 64 + index, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    /** Hook for what is specific to one widget — Split's divider, upcoming header and list.
     *  [fullW]/[fullH] are the measured widget size onUpdate already computed. No-op by default. */
    protected open fun onCustomize(context: Context, views: RemoteViews, appWidgetId: Int, palette: WidgetPalette, fullW: Int, fullH: Int) {}

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    protected fun sizePx(context: Context, manager: AppWidgetManager, id: Int): Pair<Int, Int> {
        val opts = manager.getAppWidgetOptions(id)
        val density = context.resources.displayMetrics.density
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        val w = (minW * density).toInt().coerceIn(160, 1600)
        val h = (minH * density).toInt().coerceIn(90, 1600)
        return w to h
    }

    companion object {
        /** Row-major, matching WidgetRenderer.renderMonthCalendar's cell order. */
        private val DAY_IDS = intArrayOf(
            R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6,
            R.id.d7, R.id.d8, R.id.d9, R.id.d10, R.id.d11, R.id.d12, R.id.d13,
            R.id.d14, R.id.d15, R.id.d16, R.id.d17, R.id.d18, R.id.d19, R.id.d20,
            R.id.d21, R.id.d22, R.id.d23, R.id.d24, R.id.d25, R.id.d26, R.id.d27,
            R.id.d28, R.id.d29, R.id.d30, R.id.d31, R.id.d32, R.id.d33, R.id.d34,
            R.id.d35, R.id.d36, R.id.d37, R.id.d38, R.id.d39, R.id.d40, R.id.d41,
        )

        /** Keeps day-cell request codes clear of the reminder/system ranges. */
        private const val DAY_REQUEST_BASE = 0x30000000

        /** Single source of truth for the left/right split — MUST match widget_split.xml's two
         *  LinearLayout weights (0.52 / 0.48). */
        const val SPLIT_LEFT_FRACTION = 0.52f
    }
}

class MiniMonthWidget : MonthCalendarWidget() {
    override val contentLayout = R.layout.widget_mini_month
    override val calendarViewId = R.id.miniMonthCalendar
    override val rootViewId = R.id.miniMonthRoot
}

class SplitWidget : MonthCalendarWidget() {
    override val calendarWidthFraction = SPLIT_LEFT_FRACTION
    override val contentLayout = R.layout.widget_split
    override val calendarViewId = R.id.splitCalendar
    override val rootViewId = R.id.splitRoot

    override fun onCustomize(context: Context, views: RemoteViews, appWidgetId: Int, palette: WidgetPalette, fullW: Int, fullH: Int) {
        views.setInt(R.id.splitDivider, "setBackgroundColor", palette.border)

        // Sized against the column and header weight this actually occupies, so fitXY has
        // almost nothing to stretch.
        val rightW = (fullW * (1f - calendarWidthFraction)).toInt().coerceAtLeast(1)
        val headerH = (fullH * UPCOMING_HEADER_HEIGHT_FRACTION).toInt().coerceIn(20, 160)
        views.setImageViewBitmap(R.id.splitUpcomingHeader, WidgetRenderer.renderUpcomingHeader(context, rightW, headerH))
        views.setOnClickPendingIntent(
            R.id.splitUpcomingHeader,
            PendingIntent.getActivity(
                context, appWidgetId, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        views.setTextColor(R.id.splitUpcomingEmpty, palette.textSecondary)

        val svc = WidgetCollectionService
            .intent(context, WidgetCollectionService.SCHEME_SPLIT_UPCOMING, appWidgetId)
        views.setRemoteAdapter(R.id.splitUpcomingList, svc)
        views.setEmptyView(R.id.splitUpcomingList, R.id.splitUpcomingEmpty)
        views.setPendingIntentTemplate(
            R.id.splitUpcomingList,
            PendingIntent.getActivity(
                context, appWidgetId, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )
        AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(appWidgetId, R.id.splitUpcomingList)
    }

    private companion object {
        // Must match widget_split.xml's splitUpcomingHeader weight (0.14 against the list's 0.86).
        const val UPCOMING_HEADER_HEIGHT_FRACTION = 0.14f
    }
}

/** Refresh every placed NCalendar widget after the event store changes. */
object AppWidgets {
    private val providers = listOf(
        NextEventWidget::class.java,
        DotDateWidget::class.java,
        AgendaWidget::class.java,
        MiniMonthWidget::class.java,
        UpcomingWidget::class.java,
        SplitWidget::class.java,
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
        // Every caller of refreshAll (there are many — event changes, filter toggles, ICS
        // sync, ...) is a moment the "next event" the countdown targets may have changed, so
        // re-evaluate the tick interval here rather than at each individual call site.
        WidgetTicker.sync(context)
    }
}
