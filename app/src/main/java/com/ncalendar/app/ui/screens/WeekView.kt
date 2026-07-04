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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel
import com.ncalendar.app.viewmodel.ViewMode

private const val DAY_START = 0
private const val DAY_END = 24
private val HOUR_H = 62.dp
private const val MORNING_HOUR = 7

@Composable
fun WeekView(vm: CalendarViewModel, events: List<EventItem>) {
    val state = vm.state
    val weekDays = CalendarFormats.weekDates(state.selDay, vm.weekStart)
    val gridHeight = HOUR_H * (DAY_END - DAY_START)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = ScreenPad - 8.dp, end = ScreenPad - 8.dp)) {
            Box(Modifier.width(30.dp))
            weekDays.forEach { d ->
                val isToday = d == vm.today
                val isSel = d == state.selDay
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { vm.selectDay(d); vm.setView(ViewMode.DAY) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        CalendarFormats.DOW_SHORT[CalendarFormats.dowIndex(d)],
                        color = if (d.dayOfWeek.value % 7 == 0 || d.dayOfWeek.value == 6) NColors.textDim else NColors.textMuted,
                        fontFamily = NFonts.Mono,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(if (isToday) vm.accent else if (isSel) NColors.surfaceInset else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            color = if (isToday) NColors.onInverse else if (isSel) NColors.textPrimary else NColors.dimNum,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.border))

        // Full 24h grid, opened scrolled to the morning so 00:00 isn't the landing point.
        val initialScroll = with(androidx.compose.ui.platform.LocalDensity.current) {
            (HOUR_H * MORNING_HOUR).roundToPx()
        }
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState(initialScroll))) {
            Box(Modifier.fillMaxWidth().padding(start = ScreenPad - 8.dp, end = ScreenPad - 8.dp).height(gridHeight)) {
                for (h in DAY_START until DAY_END) {
                    val top = HOUR_H * (h - DAY_START)
                    Box(Modifier.offset(y = top).padding(start = 30.dp).fillMaxWidth().height(1.dp).background(NColors.divider))
                    Text(
                        CalendarFormats.pad(h),
                        color = NColors.textFaint,
                        fontFamily = NFonts.Mono,
                        fontSize = 10.sp,
                        modifier = Modifier.offset(y = top + 4.dp).width(26.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
                Row(Modifier.fillMaxSize().padding(start = 30.dp)) {
                    weekDays.forEach { d ->
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            vm.eventsOn(events, d).filterNot { it.allDay }.forEach { e ->
                                WeekBlock(vm, e)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBlock(vm: CalendarViewModel, e: EventItem) {
    var sMin = e.start.hour * 60 + e.start.minute
    var eMin = e.end.hour * 60 + e.end.minute
    sMin = sMin.coerceAtLeast(DAY_START * 60)
    eMin = eMin.coerceAtMost(DAY_END * 60)
    if (eMin - sMin < 30) eMin = sMin + 30
    val top = HOUR_H * ((sMin - DAY_START * 60) / 60f)
    val h = HOUR_H * ((eMin - sMin) / 60f)
    val c = e.color

    Box(
        modifier = Modifier
            .offset(y = top)
            .padding(horizontal = 1.5.dp)
            .fillMaxWidth()
            .height((h - 3.dp).coerceAtLeast(12.dp))
            .background(c.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .clickable { vm.openEvent(e.id) }
            .padding(4.dp),
    ) {
        Text(e.title, color = NColors.textPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 2, lineHeight = 12.sp)
    }
}
