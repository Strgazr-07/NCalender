package com.ncalendar.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.DarkBackgroundStyle
import com.ncalendar.app.data.Prefs
import com.ncalendar.app.data.ThemeMode
import com.ncalendar.app.data.ics.IcsExportManager
import com.ncalendar.app.ui.components.Dot
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.NReminderPickerSheet
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel
import com.ncalendar.app.viewmodel.Screen

@Composable
fun SettingsScreen(vm: CalendarViewModel) {
    val context = LocalContext.current
    val events by vm.events.collectAsState()
    var defaultReminderPickerOpen by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importIcs(it, vm.defaultCalendarId()) }
    }

    Column(Modifier.fillMaxSize().background(NColors.bg).statusBarsPadding()) {
        Column(Modifier.padding(top = 12.dp, start = ScreenPad, end = ScreenPad, bottom = 8.dp)) {
            RoundIconButton("‹", onClick = { vm.backToApp() })
            Spacer(Modifier.height(18.dp))
            Text("Settings", color = NColors.textPrimary, fontSize = com.ncalendar.app.ui.theme.NType.H1, fontWeight = FontWeight.SemiBold, fontFamily = NFonts.NType82Headline)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ScreenPad),
        ) {
            SectionLabel("Appearance", top = 12.dp)
            SettingsCard {
                TabsRow(
                    title = "Theme",
                    options = listOf("System" to ThemeMode.SYSTEM, "Dark" to ThemeMode.DARK, "Light" to ThemeMode.LIGHT),
                    selected = vm.themeMode,
                    onSelect = { vm.updateThemeMode(it) },
                )
                Divider()
                TabsRow(
                    title = "Dark background",
                    options = listOf("AMOLED" to DarkBackgroundStyle.AMOLED, "Gray" to DarkBackgroundStyle.GRAY),
                    selected = vm.darkBackgroundStyle,
                    onSelect = { vm.updateDarkBackgroundStyle(it) },
                )
                Divider()
                AccentRow(selected = vm.accent, onSelect = { vm.updateAccent(it) })
                Divider()
                ToggleRow(
                    title = "Ndot display",
                    subtitle = "Dot-matrix month title & numerals",
                    checked = vm.ndot,
                    accent = vm.accent,
                    onToggle = { vm.toggleNdot() },
                )
                Divider()
                TabsRow(
                    title = "First day of week",
                    options = listOf("Sun" to 0, "Mon" to 1),
                    selected = vm.weekStart,
                    onSelect = { vm.updateWeekStart(it) },
                )
            }

            SectionLabel("Calendars")
            SettingsCard {
                NavRow("Manage calendars", value = "") { vm.go(Screen.ACCOUNTS) }
            }

            SectionLabel("ICS & subscriptions")
            SettingsCard {
                NavRow("Import .ics file", value = if (vm.usingSystemCalendar) "" else "Offline") {
                    importLauncher.launch(arrayOf("text/calendar", "text/*", "application/octet-stream"))
                }
                Divider()
                NavRow("Export calendar", value = "") {
                    IcsExportManager.shareEvents(context, vm.visibleEvents(events), "ncalendar-export.ics")
                }
            }
            vm.importMessage?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Text(
                    msg,
                    color = NColors.textMuted,
                    fontFamily = NFonts.Mono,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { vm.clearImportMessage() }.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            // Subscribed .ics / webcal feeds — its own section (renders its own header).
            if (vm.canSubscribe) {
                Spacer(Modifier.height(14.dp))
                SubscriptionsSection(vm, events)
            }

            SectionLabel("Privacy")
            SettingsCard {
                ToggleRow(
                    title = "Local-only mode",
                    subtitle = if (vm.localOnly) "Events stay on this phone" else "Use account calendars on this device",
                    checked = vm.localOnly,
                    accent = vm.accent,
                    onToggle = { vm.updateLocalOnly(!vm.localOnly) },
                )
            }

            SectionLabel("Reminders & notifications")
            SettingsCard {
                ValueRow(
                    title = "Default reminder",
                    value = CalendarFormats.reminderLabel(vm.defaultReminder).replace(" before", ""),
                    onClick = { defaultReminderPickerOpen = true },
                )
                Divider()
                NavRow("System notifications", value = "") {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    runCatching { context.startActivity(intent) }
                }
            }

            val versionName = androidx.compose.runtime.remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "1.0"
            }
            SectionLabel("About")
            SettingsCard {
                InfoRow("Version", versionName)
                Divider()
                InfoRow("Developer", "Shrijesh")
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "NCalendar $versionName · for Nothing OS",
                color = NColors.textGhostDeep,
                fontFamily = NFonts.Mono,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (defaultReminderPickerOpen) {
        NReminderPickerSheet(
            accent = vm.accent,
            ndot = vm.ndot,
            onDismiss = { defaultReminderPickerOpen = false },
            onConfirm = {
                vm.updateDefaultReminder(it)
                defaultReminderPickerOpen = false
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 26.dp) {
    MonoLabel(text, color = NColors.textFaint, size = 11.sp, modifier = Modifier.padding(top = top, bottom = 12.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(NColors.surface, RoundedCornerShape(18.dp))
            .border(1.dp, NColors.border, RoundedCornerShape(18.dp)),
        content = { content() },
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(NColors.border))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, accent: Color, onToggle: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NColors.textPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = NColors.textFaint, fontSize = 13.sp)
        }
        AnimatedSwitch(checked = checked, accent = accent)
    }
}

private val accentPalette = listOf(
    "Red" to Prefs.DEFAULT_ACCENT,
    "Blue" to 0xFF2D7DFF.toInt(),
    "Green" to 0xFF18A558.toInt(),
    "Amber" to 0xFFE0A100.toInt(),
    "Pink" to 0xFFD9488F.toInt(),
    "White" to 0xFFE8E8E6.toInt(),
)

@Composable
private fun AccentRow(selected: Color, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Accent color", color = NColors.textPrimary, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            accentPalette.forEach { (_, argb) ->
                val color = Color(argb)
                val selectedHere = selected.toArgb() == argb
                Box(
                    Modifier
                        .size(24.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (selectedHere) 2.dp else 1.dp,
                            color = if (selectedHere) NColors.textPrimary else NColors.borderStrong,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(argb) },
                )
            }
        }
    }
}

@Composable
fun AnimatedSwitch(checked: Boolean, accent: Color) {
    val knobOffset by animateDpAsState(if (checked) 22.dp else 3.dp, label = "knob")
    val trackColor by animateColorAsState(if (checked) accent else NColors.surfaceInset, label = "track")
    Box(
        Modifier.width(48.dp).height(28.dp).background(trackColor, RoundedCornerShape(20.dp)),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(22.dp)
                .background(NColors.inverseBg, CircleShape),
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = NColors.textPrimary, fontSize = 16.sp)
        Text(value, color = NColors.textMuted, fontFamily = NFonts.Mono, fontSize = 14.sp)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = NColors.textPrimary, fontSize = 16.sp)
        Text(value, color = NColors.textMuted, fontFamily = NFonts.Mono, fontSize = 14.sp)
    }
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = NColors.textPrimary, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) {
                Text(value, color = NColors.textMuted, fontFamily = NFonts.Mono, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text("›", color = NColors.textDim, fontSize = 18.sp)
        }
    }
}

@Composable
private fun <T> TabsRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = NColors.textPrimary, fontSize = 16.sp)
        Row(Modifier.background(NColors.surfaceHi, RoundedCornerShape(11.dp)).padding(4.dp)) {
            options.forEach { (label, value) ->
                val sel = value == selected
                Text(
                    label,
                    color = if (sel) NColors.onInverse else NColors.textDim,
                    fontFamily = NFonts.Mono,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .background(if (sel) NColors.textPrimary else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
