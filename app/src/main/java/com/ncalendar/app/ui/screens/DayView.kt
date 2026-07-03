package com.ncalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel
import com.ncalendar.app.viewmodel.DragState
import kotlin.math.roundToInt

private const val DAY_START = 0
private const val DAY_END = 24
private val HOUR_H = 66.dp
private val GUTTER = 60.dp
private const val MORNING_HOUR = 7

@Composable
fun DayView(vm: CalendarViewModel, events: List<EventItem>) {
    val state = vm.state
    val dayEvents = vm.eventsOn(events, state.selDay)
    val allDay = dayEvents.filter { it.allDay }
    val timed = dayEvents.filterNot { it.allDay }
    val now by vm.now.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPad, vertical = 10.dp)) {
                allDay.forEach { e ->
                    val c = e.color
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(c.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { vm.openEvent(e.id) }
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.ncalendar.app.ui.components.Dot(c, size = 8.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(e.title, color = NColors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        MonoLabel("ALL-DAY", color = NColors.textFainter, size = 10.sp)
                    }
                }
            }
        }

        val density = LocalDensity.current
        val hourHPx = with(density) { HOUR_H.toPx() }
        val gridHeight = HOUR_H * (DAY_END - DAY_START)

        // Full 24h grid; land on the morning (or an hour before now on today).
        val startHour = if (state.selDay == vm.today) (now.hour - 1).coerceAtLeast(0) else MORNING_HOUR
        val initialScroll = with(density) { (HOUR_H * startHour).roundToPx() }
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState(initialScroll))) {
            Box(Modifier.fillMaxWidth().height(gridHeight)) {
                for (h in DAY_START until DAY_END) {
                    val top = HOUR_H * (h - DAY_START)
                    Box(Modifier.offset(y = top).padding(start = GUTTER - 4.dp).fillMaxWidth().height(1.dp).background(NColors.divider))
                    Text(
                        CalendarFormats.fmtHour(h),
                        color = NColors.textFainter,
                        fontSize = 11.sp,
                        fontFamily = NFonts.Mono,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(y = top + 4.dp).width(GUTTER - 12.dp),
                    )
                }

                timed.forEach { e ->
                    DayBlock(vm, events, e, hourHPx)
                }

                if (state.selDay == vm.today) {
                    val nowMin = now.hour * 60 + now.minute
                    val topLine = HOUR_H * ((nowMin - DAY_START * 60) / 60f)
                    Box(
                        Modifier
                            .offset(y = topLine)
                            .padding(start = GUTTER - 12.dp, end = ScreenPad)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(vm.accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayBlock(vm: CalendarViewModel, events: List<EventItem>, e: EventItem, hourHPx: Float) {
    val state = vm.state
    val dragDm = if (state.drag?.eventId == e.id) state.drag.deltaMinutes else 0
    var sMin = e.start.hour * 60 + e.start.minute
    var eMin = e.end.hour * 60 + e.end.minute
    sMin = sMin.coerceAtLeast(DAY_START * 60)
    eMin = eMin.coerceAtMost(DAY_END * 60)
    if (eMin - sMin < 30) eMin = sMin + 30
    val top = HOUR_H * ((sMin - DAY_START * 60) / 60f)
    val h = HOUR_H * ((eMin - sMin) / 60f)
    val c = Calendars.get(e.calendarId).color
    val dragging = dragDm != 0
    val dragOffsetDp = with(LocalDensity.current) { (dragDm / 60f * hourHPx).toDp() }

    val timeLabel = if (dragging) {
        val newStartMin = (e.start.hour * 60 + e.start.minute + dragDm).coerceIn(0, 1439)
        "→ ${CalendarFormats.pad(newStartMin / 60)}:${CalendarFormats.pad(newStartMin % 60)}"
    } else CalendarFormats.timeLabelFor(e)

    Row(
        modifier = Modifier
            .offset(y = top + dragOffsetDp)
            .padding(start = GUTTER, end = ScreenPad)
            .fillMaxWidth()
            .height(h - 4.dp)
            .background(c.copy(alpha = if (dragging) 0.24f else 0.14f), RoundedCornerShape(10.dp))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxSize().background(c, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .pointerInput(e.id) {
                    detectTapGestures(
                        onTap = { vm.openEvent(e.id) },
                        onLongPress = { vm.openMenu(e.id) },
                    )
                },
        ) {
            Text(e.title, color = NColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(timeLabel, color = if (dragging) vm.accent else NColors.textMuted, fontSize = 12.sp, fontFamily = NFonts.Mono)
        }
        DragHandle(vm, events, e.id, hourHPx)
    }
}

@Composable
private fun DragHandle(vm: CalendarViewModel, events: List<EventItem>, eventId: String, hourHPx: Float) {
    var accumPx by remember { mutableFloatStateOf(0f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(24.dp)
            .pointerInput(eventId) {
                detectVerticalDragGestures(
                    onDragStart = { accumPx = 0f },
                    onDragEnd = {
                        val dm = ((accumPx / hourHPx * 60) / 15).roundToInt() * 15
                        if (dm != 0) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        vm.shiftEvent(events, eventId, dm)
                        accumPx = 0f
                    },
                    onDragCancel = { vm.setDrag(null); accumPx = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    accumPx += dragAmount
                    val dm = ((accumPx / hourHPx * 60) / 15).roundToInt() * 15
                    vm.setDrag(DragState(eventId, dm))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("⋮⋮", color = NColors.textGhost, fontSize = 13.sp)
    }
}

