package com.ncalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel

@Composable
fun DayEventsScreen(vm: CalendarViewModel) {
    val all by vm.events.collectAsState()
    val day = vm.state.selDay
    val events = vm.eventsOn(all, day)
    val title = if (day == vm.today) "Today's Events" else CalendarFormats.fmtDateFull(day, vm.today)

    Column(Modifier.fillMaxSize().background(NColors.bg).statusBarsPadding()) {
        Column(Modifier.padding(top = 12.dp, start = ScreenPad, end = ScreenPad, bottom = 16.dp)) {
            RoundIconButton("<", onClick = { vm.backToApp() })
            Spacer(Modifier.height(20.dp))
            MonoLabel(
                "${CalendarFormats.DOW[CalendarFormats.dowIndex(day)]} - ${CalendarFormats.MON[day.monthValue - 1]} ${day.dayOfMonth}",
                color = if (day == vm.today) vm.accent else NColors.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = NColors.textPrimary,
                fontSize = com.ncalendar.app.ui.theme.NType.H1,
                fontWeight = FontWeight.SemiBold,
                fontFamily = NFonts.NType82Headline,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ScreenPad),
        ) {
            if (events.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(NColors.surface, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    MonoLabel("Nothing scheduled", color = NColors.textFaint)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .height(42.dp)
                            .background(NColors.inverseBg, RoundedCornerShape(12.dp))
                            .clickable { vm.openNewEvent(day) }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MonoLabel("Add event", color = NColors.onInverse)
                    }
                    Spacer(Modifier.weight(1f))
                }
            } else {
                events.forEachIndexed { index, event ->
                    DayEventRow(vm, event)
                    if (index != events.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.divider))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DayEventRow(vm: CalendarViewModel, event: EventItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openEvent(event.id) }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(event.color, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, color = NColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(CalendarFormats.timeLabelFor(event), color = NColors.textMuted, fontFamily = NFonts.Mono, fontSize = 12.sp)
        }
        Text(">", color = NColors.textDim, fontSize = 20.sp)
    }
}
