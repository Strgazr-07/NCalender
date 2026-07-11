package com.ncalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun MonthView(vm: CalendarViewModel, events: List<EventItem>) {
    val state = vm.state
    val cells = CalendarFormats.monthGridDates(state.anchor, vm.weekStart)
    val dowLabels = CalendarFormats.dowShortLabels(vm.weekStart)
    val dayIndex = vm.buildDayIndex(events)

    // The page itself doesn't scroll: the weekday row, month grid and day label
    // stay pinned, and only the selected day's event list (below) gets a scroll
    // region — so tapping a busy day no longer pushes the calendar off the top.
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = ScreenPad),
    ) {
        // Symmetric breathing room around the weekday letters, and brighter text —
        // the faint tones were hard to read on the pure-black background.
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            dowLabels.forEachIndexed { i, l ->
                val idx = (vm.weekStart + i) % 7
                Text(
                    l,
                    color = if (idx == 0 || idx == 6) NColors.textDim else NColors.textMuted,
                    fontFamily = NFonts.Mono,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // The grid flexes to fill the space above the day-summary card, so the card is
        // always visible without scrolling — the whole point of this redesign.
        Column(Modifier.fillMaxWidth().aspectRatio(7f / 6f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            MonthCell(vm, dayIndex[date].orEmpty(), date)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // A compact, tappable summary of the selected day — date + event count with a
        // peek of the first couple events. Tap it (or double-tap the grid cell) to open
        // the day's full timeline, instead of scrolling a cramped strip below the grid.
        val selD = state.selDay
        DaySummaryCard(vm, selD, dayIndex[selD].orEmpty())
        Spacer(Modifier.height(88.dp)) // leave room for the floating add button
    }
}

@Composable
private fun DaySummaryCard(vm: CalendarViewModel, day: LocalDate, events: List<EventItem>) {
    val dateLabel = "${CalendarFormats.DOW[CalendarFormats.dowIndex(day)]}, ${CalendarFormats.MON[day.monthValue - 1]} ${day.dayOfMonth}"
    Column(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(NColors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, NColors.border, RoundedCornerShape(16.dp))
            .clickable { vm.openDayEvents(day) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dateLabel, color = NColors.textSecondary, fontFamily = NFonts.Mono, fontSize = 13.sp, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (events.isEmpty()) "Nothing scheduled" else "${events.size} event${if (events.size == 1) "" else "s"}",
                    color = if (events.isEmpty()) NColors.textFaint else vm.accent,
                    fontFamily = NFonts.Mono,
                    fontSize = 12.sp,
                )
                if (events.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text("›", color = vm.accent, fontSize = 17.sp)
                }
            }
        }
        if (events.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            events.take(1).forEachIndexed { i, e ->
                if (i != 0) Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (NColors.isDark) e.color else NColors.textMuted, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (e.allDay) "ALL" else CalendarFormats.fmtTime(e.start.toLocalTime()),
                        color = NColors.textMuted,
                        fontFamily = NFonts.numeral(vm.ndot),
                        fontSize = 13.sp,
                        modifier = Modifier.width(52.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(e.title, color = NColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
            if (events.size > 1) {
                Spacer(Modifier.height(11.dp))
                Text("+${events.size - 1} more", color = NColors.textFaint, fontFamily = NFonts.Mono, fontSize = 12.sp)
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
            .fillMaxSize()
            .background(if (isSel) NColors.surfaceSel else Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, if (isSel) NColors.textGhostDeep else Color.Transparent, RoundedCornerShape(12.dp))
            .pointerInput(date) {
                // Single tap selects (updates the summary card); double tap opens the day.
                detectTapGestures(
                    onTap = { vm.selectDay(date) },
                    onDoubleTap = { vm.openDayEvents(date) },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = when {
                isToday -> vm.accent
                inMonth -> NColors.textPrimary
                else -> NColors.textGhost
            },
            fontSize = 19.sp,
            fontWeight = when {
                isToday -> FontWeight.Bold
                inMonth -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            dayEvents.forEach { e ->
                val dim = !inMonth
                val ink = if (dim) vm.accent.copy(alpha = 0.35f) else vm.accent
                if (e.allDay) {
                    Box(
                        Modifier.size(6.dp)
                            .border(1.5.dp, ink, CircleShape),
                    )
                } else {
                    Dot(ink, size = 6.dp)
                }
            }
            if (dayEvents.isEmpty()) Spacer(Modifier.size(6.dp))
        }
    }
}
