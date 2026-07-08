package com.ncalendar.app.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.ui.components.Dot
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel
import com.ncalendar.app.viewmodel.Screen

@Composable
fun SettingsScreen(vm: CalendarViewModel) {
    val context = LocalContext.current
    val events by vm.events.collectAsState()

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
                    options = listOf("Dark" to true, "Light" to false),
                    selected = vm.darkTheme,
                    onSelect = { vm.updateDarkTheme(it) },
                )
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

            // Subscribed .ics / webcal feeds — its own section (renders its own header).
            if (vm.canSubscribe) {
                Spacer(Modifier.height(26.dp))
                SubscriptionsSection(vm, events)
            }

            SectionLabel("Reminders & notifications")
            SettingsCard {
                ValueRow(
                    title = "Default reminder",
                    value = CalendarFormats.reminderLabel(vm.defaultReminder).replace(" before", ""),
                    onClick = { vm.cycleDefaultReminder() },
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
