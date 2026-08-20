package com.ncalendar.app

import android.app.Application
import com.ncalendar.app.data.Prefs
import com.ncalendar.app.data.ics.IcsSyncWorker
import com.ncalendar.app.notifications.NotificationHelper
import com.ncalendar.app.notifications.ReminderScheduler
import com.ncalendar.app.notifications.ReminderSyncWorker

class NCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        // Must run before anything else reads prefs, so upgrading users get the
        // migration guards (see Prefs.migrate) rather than a fresh install's defaults.
        prefs.migrate()
        NotificationHelper.ensureChannel(this)
        // Ghost-notification cleanup for the pre-V2 alarm request-code scheme. Idempotent and
        // self-latching, so this is a no-op on every start after the first.
        ReminderScheduler.cancelLegacyAlarms(this)
        // Two independent horizon-rolling mechanisms — see ReminderSyncWorker's KDoc for why.
        ReminderSyncWorker.schedulePeriodic(this)
        ReminderScheduler.armDailySync(this)
        // Only keep the periodic feed refresh alive while there's something to sync.
        if (prefs.icsSubscriptions.isNotEmpty()) IcsSyncWorker.schedulePeriodic(this)
    }
}
