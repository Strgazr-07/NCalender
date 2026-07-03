package com.ncalendar.app.ui.screens

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
import com.ncalendar.app.ui.components.Dot
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel
import java.time.LocalDate

private val ROW_HEIGHT = 64.dp

@Composable
fun MonthView(vm: CalendarViewModel, events: List<EventItem>) {
    val state = vm.state
    val cells = CalendarFormats.monthGridDates(state.anchor, vm.weekStart)
    val dowLabels = CalendarFormats.dowShortLabels(vm.weekStart)
    val dayIndex = vm.buildDayIndex(events)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPad),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            dowLabels.forEachIndexed { i, l ->
                val idx = (vm.weekStart + i) % 7
                Text(
                    l,
                    color = if (idx == 0 || idx == 6) NColors.textFainter else NColors.textDim,
                    fontFamily = NFonts.Mono,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Plain fixed grid — all 42 cells are always visible, so lazy-grid
        // bookkeeping inside the scrollable column was pure overhead.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f)) {
                            MonthCell(vm, dayIndex[date].orEmpty(), date)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.border))
        Spacer(Modifier.height(18.dp))

        val selD = state.selDay
        MonoLabel(
            "${CalendarFormats.DOW[CalendarFormats.dowIndex(selD)]}, ${CalendarFormats.MON[selD.monthValue - 1]} ${selD.dayOfMonth}",
            color = NColors.textDim,
            size = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        val selEvents = dayIndex[selD].orEmpty()
        if (selEvents.isEmpty()) {
            Text(
                "Nothing scheduled.",
                color = NColors.textGhost,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 22.dp),
            )
        } else {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 96.dp)) {
                selEvents.forEachIndexed { i, e ->
                    if (i != 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.divider))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openEvent(e.id) }
                            .padding(vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (e.allDay) "ALL" else CalendarFormats.fmtTime(e.start.toLocalTime()),
                            color = NColors.textMuted,
                            fontFamily = NFonts.numeral(vm.ndot),
                            fontSize = 14.sp,
                            modifier = Modifier.width(54.dp),
                        )
                        Box(
                            Modifier.width(4.dp).height(34.dp)
                                .background(e.color, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, color = NColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            val meta = (e.location?.let { "$it · " } ?: "") + e.calendarName
                            Text(meta, color = NColors.textFaint, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(vm: CalendarViewModel, dayEventsAll: List<EventItem>, date: LocalDate) {
    val state = vm.state
    val inMonth = date.monthValue == state.anchor.monthValue
    val isToday = date == vm.today
    val isSel = date == state.selDay
    val dayEvents = dayEventsAll.take(3)

    Column(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (isSel) NColors.surfaceSel else Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, if (isSel) NColors.textGhostDeep else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { vm.selectDay(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = when {
                isToday -> vm.accent
                inMonth -> NColors.textPrimary
                else -> NColors.textGhostDeep
            },
            fontSize = 18.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            dayEvents.forEach { e ->
                val dim = !inMonth
                if (e.allDay) {
                    Box(
                        Modifier.size(6.dp)
                            .border(1.5.dp, if (dim) NColors.textGhostDeep else NColors.textDim, CircleShape),
                    )
                } else {
                    Dot(if (dim) NColors.textGhostDeep else e.color, size = 6.dp)
                }
            }
            if (dayEvents.isEmpty()) Spacer(Modifier.size(6.dp))
        }
    }
}
