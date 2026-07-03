package com.ncalendar.app

import android.app.Application
import com.ncalendar.app.notifications.NotificationHelper

class NCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }
}
