package com.ncalendar.app

import android.app.Application
import com.ncalendar.app.data.Prefs
import com.ncalendar.app.data.ics.IcsSyncWorker
import com.ncalendar.app.notifications.NotificationHelper

class NCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        // Only keep the periodic feed refresh alive while there's something to sync.
        if (Prefs(this).icsSubscriptions.isNotEmpty()) IcsSyncWorker.schedulePeriodic(this)
    }
}
