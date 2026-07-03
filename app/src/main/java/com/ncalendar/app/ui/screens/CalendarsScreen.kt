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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.Calendars
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
    val accounts = calendars.groupBy { it.accountName.ifBlank { "On this device" } }.toList()
    val subtitle = if (vm.usingSystemCalendar) "Synced via Android accounts" else "Demo · grant access to see real calendars"

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

            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(if (vm.usingSystemCalendar) Color(0xFF8FAE8B) else NColors.textFainter, size = 6.dp)
                Spacer(Modifier.width(9.dp))
                MonoLabel(
                    if (vm.usingSystemCalendar) "Reading your device calendars" else "Showing demo data",
                    color = NColors.textFaint,
                )
            }
        }
    }
}
