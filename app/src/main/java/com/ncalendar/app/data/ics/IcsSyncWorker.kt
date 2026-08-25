package com.ncalendar.app.data.ics

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ncalendar.app.data.EventRepository
import com.ncalendar.app.notifications.ReminderScheduler
import com.ncalendar.app.widget.AppWidgets
import java.util.concurrent.TimeUnit

/** Periodic + on-demand background refresh of subscribed .ics calendars. */
class IcsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        IcsSyncManager.syncAll(applicationContext)
        // Re-read the provider (now including refreshed subscription events), re-arm alarms
        // for anything the sync added or moved, and repaint widgets — so the update lands
        // even if the UI is closed.
        val repo = EventRepository.get(applicationContext)
        repo.refresh(force = true)
        ReminderScheduler.syncAll(applicationContext, repo.events.value)
        AppWidgets.refreshAll(applicationContext)
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private const val PERIODIC = "ics_periodic_sync"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Idempotent — keeps any already-scheduled work. Call once subscriptions exist. */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<IcsSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
