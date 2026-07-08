package com.ncalendar.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.ics.IcsSubscription
import com.ncalendar.app.data.ics.SubscriptionCalendars
import com.ncalendar.app.ui.components.Dot
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel

@Composable
fun CalendarsScreen(vm: CalendarViewModel) {
    val events by vm.events.collectAsState()
    val calendars by vm.calendars.collectAsState()
    // Subscribed feeds get their own section below, so keep them out of the
    // regular account groups (they're a device-local mirror, not a real account).
    val accounts = calendars
        .filterNot { it.accountName == SubscriptionCalendars.ACCOUNT_NAME }
        .groupBy { it.accountName.ifBlank { "On this device" } }.toList()
    val subtitle = if (vm.usingSystemCalendar) "Synced via Android accounts" else "Local only · private to this phone"

    Column(Modifier.fillMaxSize().background(NColors.bg).statusBarsPadding()) {
        Column(Modifier.padding(top = 12.dp, start = ScreenPad, end = ScreenPad, bottom = 8.dp)) {
            RoundIconButton("‹", onClick = { vm.backToApp() })
            Spacer(Modifier.height(18.dp))
            Text("Calendars", color = NColors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, fontFamily = NFonts.NType82Headline)
            Spacer(Modifier.height(4.dp))
            MonoLabel(subtitle, color = NColors.textFainter)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ScreenPad),
        ) {
            accounts.forEach { (account, cals) ->
                // Account header — icon + email left-aligned to the screen edge.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 22.dp, bottom = 12.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).background(NColors.surfaceSel2, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(account.take(1).uppercase(), color = NColors.textPrimary, fontFamily = NFonts.Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(account, color = NColors.textPrimary, fontSize = 16.sp)
                        Spacer(Modifier.height(2.dp))
                        MonoLabel(cals.firstOrNull()?.accountType?.ifBlank { "Local" } ?: "Local", color = NColors.textFainter)
                    }
                }

                // Calendars for this account — one card, rows share a left edge, dividers between.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(NColors.surface, RoundedCornerShape(18.dp))
                        .border(1.dp, NColors.border, RoundedCornerShape(18.dp)),
                ) {
                    cals.forEachIndexed { i, c ->
                        val on = vm.isCalendarVisible(c.id)
                        val count = events.count { it.calendarId == c.id }
                        if (i != 0) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(NColors.border))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleCalendarVisible(c.id) }
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val fill by animateColorAsState(if (on) c.color else Color.Transparent, label = "cb")
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(fill, RoundedCornerShape(6.dp))
                                    .border(2.dp, c.color, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Text("✓", color = Color(0xFF15150F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                c.name,
                                color = if (on) NColors.textPrimary else NColors.textFainter,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            MonoLabel("$count events", color = NColors.textFainter, size = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // Connect / sign out — the privacy switch between account calendars
            // and the on-device local store.
            if (vm.usingSystemCalendar) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(NColors.surface, RoundedCornerShape(18.dp))
                        .border(1.dp, NColors.border, RoundedCornerShape(18.dp))
                        .clickable { vm.updateLocalOnly(true) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text("Sign out — use offline", color = vm.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Stop reading account calendars. New events are stored only on this phone; your Google events stay untouched in your account.",
                        color = NColors.textFaint, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(NColors.surface, RoundedCornerShape(18.dp))
                        .border(1.dp, NColors.border, RoundedCornerShape(18.dp))
                        .clickable { vm.updateLocalOnly(false) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text("Connect account calendars", color = NColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Read and edit your Google, Outlook and device calendars. You can sign out again anytime.",
                        color = NColors.textFaint, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(if (vm.usingSystemCalendar) Color(0xFF8FAE8B) else vm.accent, size = 6.dp)
                Spacer(Modifier.width(9.dp))
                MonoLabel(
                    if (vm.usingSystemCalendar) "Reading your device calendars" else "Nothing leaves this phone",
                    color = NColors.textFaint,
                )
            }
        }
    }
}

// ---------------- subscribed .ics calendars ----------------

private val subPalette = listOf(
    0xFFD71921.toInt(), 0xFF6F9DB8.toInt(), 0xFFC2A878.toInt(),
    0xFF8FAE8B.toInt(), 0xFFB08BC7.toInt(), 0xFFE8E8E6.toInt(),
)

@Composable
internal fun SubscriptionsSection(vm: CalendarViewModel, events: List<EventItem>) {
    var adding by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            "Subscribed calendars",
            color = NColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (vm.subscriptions.isNotEmpty()) {
            Text(
                if (vm.syncingSubs) "Syncing…" else "Refresh",
                color = if (vm.syncingSubs) NColors.textFainter else vm.accent,
                fontFamily = NFonts.Mono, fontSize = 12.sp,
                modifier = Modifier
                    .clickable(enabled = !vm.syncingSubs) { vm.refreshSubscriptions() }
                    .padding(6.dp),
            )
        }
    }

    if (vm.subscriptions.isEmpty()) {
        Text(
            "Add a webcal or .ics link — an iCloud shared calendar, a team schedule, a ferry timetable — and it refreshes in the background.",
            color = NColors.textFaint, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .background(NColors.surface, RoundedCornerShape(18.dp))
                .border(1.dp, NColors.border, RoundedCornerShape(18.dp)),
        ) {
            vm.subscriptions.forEachIndexed { i, sub ->
                if (i != 0) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(NColors.border))
                SubscriptionRow(vm, sub, events)
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (adding) {
        AddSubscriptionForm(
            accent = vm.accent,
            onCancel = { adding = false },
            onAdd = { url, name, color -> vm.addSubscription(url, name, color); adding = false },
        )
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .background(NColors.surface, RoundedCornerShape(18.dp))
                .border(1.dp, NColors.border, RoundedCornerShape(18.dp))
                .clickable { adding = true }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+", color = vm.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text("Add .ics subscription", color = NColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SubscriptionRow(vm: CalendarViewModel, sub: IcsSubscription, events: List<EventItem>) {
    val calId = sub.calendarId?.toString()
    val on = calId != null && vm.isCalendarVisible(calId)
    val color = Color(sub.colorArgb)
    val status = when {
        sub.lastError != null -> "⚠ ${sub.lastError}"
        sub.lastSyncEpoch <= 0L -> "Syncing…"
        else -> {
            val count = if (calId != null) events.count { it.calendarId == calId } else 0
            "$count events · updated ${relTime(sub.lastSyncEpoch)}"
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (on) color else Color.Transparent, RoundedCornerShape(6.dp))
                .border(2.dp, color, RoundedCornerShape(6.dp))
                .clickable(enabled = calId != null) { calId?.let { vm.toggleCalendarVisible(it) } },
            contentAlignment = Alignment.Center,
        ) {
            if (on) Text("✓", color = Color(0xFF15150F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(sub.name, color = if (on) NColors.textPrimary else NColors.textFainter, fontSize = 16.sp, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                status,
                color = if (sub.lastError != null) vm.accent else NColors.textFaint,
                fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Remove",
            color = NColors.textFainter, fontFamily = NFonts.Mono, fontSize = 11.sp,
            modifier = Modifier.clickable { vm.removeSubscription(sub.id) }.padding(6.dp),
        )
    }
}

@Composable
private fun AddSubscriptionForm(accent: Color, onCancel: () -> Unit, onAdd: (String, String, Int) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(subPalette.first()) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(NColors.surface, RoundedCornerShape(18.dp))
            .border(1.dp, NColors.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        MonoLabel("Feed URL", color = NColors.textFaint)
        SubField(url, "webcal:// or https://…/calendar.ics") { url = it }
        Spacer(Modifier.height(12.dp))
        MonoLabel("Name", color = NColors.textFaint)
        SubField(name, "e.g. Emma's calendar") { name = it }
        Spacer(Modifier.height(16.dp))
        MonoLabel("Colour", color = NColors.textFaint)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            subPalette.forEach { c ->
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(c), CircleShape)
                        .border(if (color == c) 2.dp else 0.dp, NColors.textPrimary, CircleShape)
                        .clickable { color = c },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cancel", color = NColors.textDim, fontFamily = NFonts.Mono, fontSize = 13.sp,
                modifier = Modifier.clickable { onCancel() }.padding(8.dp),
            )
            Spacer(Modifier.width(10.dp))
            val enabled = url.isNotBlank()
            Text(
                "Add",
                color = if (enabled) accent else NColors.textFainter,
                fontFamily = NFonts.Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = enabled) { onAdd(url.trim(), name.trim(), color) }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun SubField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = NColors.textGhost, fontSize = 14.sp) },
        singleLine = true,
        textStyle = TextStyle(color = NColors.textPrimary, fontSize = 15.sp, fontFamily = NFonts.Body),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = NColors.borderStrong,
            unfocusedIndicatorColor = NColors.border,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun relTime(epoch: Long): String {
    val mins = (System.currentTimeMillis() - epoch) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}
