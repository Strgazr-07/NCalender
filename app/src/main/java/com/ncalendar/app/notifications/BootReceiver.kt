package com.ncalendar.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ncalendar.app.data.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Re-arms all event reminders after a reboot (alarms are cleared on shutdown). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = EventRepository.get(appContext)
                repo.refresh(LocalDate.now())
                ReminderScheduler.syncAll(appContext, repo.events.value)
            } finally {
                pending.finish()
            }
        }
    }
}
