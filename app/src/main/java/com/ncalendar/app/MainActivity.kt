package com.ncalendar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.ncalendar.app.data.Prefs
import com.ncalendar.app.ui.AppRoot
import com.ncalendar.app.ui.PermissionPrimingScreen
import com.ncalendar.app.ui.theme.NCalendarTheme
import com.ncalendar.app.viewmodel.CalendarViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels()
    private val prefs by lazy { Prefs(this) }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Bumped whenever permission state may have changed, so composition re-checks it. */
    private var permVersion by mutableStateOf(0)

    private val requestCalendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.onResume() // re-read real calendars if granted
            permVersion++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOpenEvent(intent)
        setContent {
            val dark = viewModel.darkTheme
            androidx.compose.runtime.SideEffect {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            NCalendarTheme(dark = dark, accent = viewModel.accent) {
                // Priming stays up until access is granted — unless the user opted
                // for local-only mode, which needs no permission at all.
                val showPriming = !viewModel.localOnly && (permVersion < 0 || !hasCalendarPermission())
                if (showPriming) {
                    PermissionPrimingScreen(
                        accent = viewModel.accent,
                        onGrant = {
                            // Once Android stops showing the dialog (denied twice),
                            // the only path left is the app's settings page.
                            val canAsk = !prefs.permissionPrimed ||
                                shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
                            prefs.permissionPrimed = true
                            if (canAsk) requestAllPermissions() else openAppSettings()
                        },
                        onUseLocal = {
                            viewModel.updateLocalOnly(true)
                            // Notifications are still wanted for local reminders.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                } else {
                    AppRoot(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenEvent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        permVersion++ // permission may have been granted from the settings page
    }

    private fun openAppSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null),
        )
        runCatching { startActivity(intent) }
    }

    private fun handleOpenEvent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_EVENT_ID)?.let { id ->
            if (id.isNotBlank()) viewModel.openEvent(id)
        }
    }

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!hasCalendarPermission()) {
            requestCalendarPermission.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    companion object {
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
    }
}
