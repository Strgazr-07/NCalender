package com.ncalendar.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ncalendar.app.data.EventRepository
import java.util.concurrent.TimeUnit

/**
 * Rolls the reminder alarm horizon forward in the background. Without this, alarms were only
 * ever (re-)armed when the app was opened, when the event list changed while it was already
 * open, or on boot — so an event further out than the horizon (a birthday, most obviously)
 * never got an alarm at all unless the user happened to open the app inside its final window.
 *
 * Deliberately paired with ReminderScheduler.armDailySync's self-perpetuating alarm: periodic
 * WorkManager jobs are not reliably honored on Doze-heavy OEM ROMs, and "reminders sometimes
 * just never fire" is exactly the failure this is meant to eliminate, so it gets two
 * independent mechanisms rather than one.
 */
class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val repo = EventRepository.get(applicationContext)
        repo.refresh(force = true)
        ReminderScheduler.syncAll(applicationContext, repo.events.value)
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private const val PERIODIC = "reminder_periodic_sync"

        /** Idempotent — safe to call on every app start. No network constraint: this only
         *  touches the local provider and AlarmManager. */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderSyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
