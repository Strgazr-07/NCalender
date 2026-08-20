package com.ncalendar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViewsService

/**
 * The single RemoteViewsService behind every collection-backed widget (month grid, Split's
 * upcoming column, the standalone Upcoming list).
 *
 * There used to be one service class per collection. They were structurally identical —
 * `onGetViewFactory` differing only in which factory it returned — and in a minified release
 * build R8 fused them, so a widget could be handed the WRONG collection's factory: the month
 * grid rendered event rows while the upcoming list rendered a single giant date cell. Debug
 * builds were unaffected, which is what made it look like a layout bug rather than a build one.
 *
 * Dispatching on the intent's data Uri instead of on the service class removes the ambiguity
 * for good. The scheme is part of Intent.filterEquals(), which is precisely the key both
 * RemoteViewsService's factory cache and the host's RemoteViewsAdapter cache use — so each
 * collection maps to exactly one factory no matter what the optimizer does to the class graph.
 */
class WidgetCollectionService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return when (intent.data?.scheme) {
            SCHEME_SPLIT_UPCOMING -> SplitUpcomingFactory(applicationContext, widgetId)
            else -> UpcomingFactory(applicationContext, widgetId)
        }
    }

    companion object {
        const val SCHEME_SPLIT_UPCOMING = "ncalendar-split-upcoming"
        const val SCHEME_UPCOMING = "ncalendar-upcoming"

        /**
         * Builds the adapter intent for one collection on one widget. The Uri carries BOTH the
         * collection kind (scheme) and the widget id (ssp), so two widgets of the same kind get
         * separate adapters and two collections on the SAME widget never share one — extras
         * can't do this job, since filterEquals ignores them.
         */
        fun intent(context: Context, scheme: String, appWidgetId: Int): Intent =
            Intent(context, WidgetCollectionService::class.java).apply {
                data = Uri.fromParts(scheme, appWidgetId.toString(), null)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
    }
}
