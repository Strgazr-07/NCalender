package com.ncalendar.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.ui.components.TrashGlyph
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AgendaView(vm: CalendarViewModel, events: List<EventItem>) {
    val dayIndex = vm.buildDayIndex(events)
    val groups = buildList {
        var d = vm.today
        repeat(30) {
            val evs = dayIndex[d].orEmpty()
            if (evs.isNotEmpty()) add(d to evs)
            d = d.plusDays(1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = ScreenPad, vertical = 8.dp),
    ) {
        groups.forEach { (day, evs) ->
            val isToday = day == vm.today
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp)) {
                Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        CalendarFormats.DOW[CalendarFormats.dowIndex(day)],
                        color = if (isToday) vm.accent else NColors.textDim,
                        fontFamily = NFonts.Mono,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        day.dayOfMonth.toString(),
                        color = if (isToday) vm.accent else NColors.textPrimary,
                        fontFamily = NFonts.numeral(vm.ndot),
                        fontSize = 32.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        CalendarFormats.MON[day.monthValue - 1],
                        color = NColors.textFainter,
                        fontFamily = NFonts.Mono,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    evs.forEachIndexed { idx, e ->
                        AgendaRow(vm, e, showDivider = idx != evs.lastIndex)
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun AgendaRow(vm: CalendarViewModel, e: EventItem, showDivider: Boolean) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 120.dp.toPx() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(vm.accent.copy(alpha = 0.16f)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(end = 14.dp)) { TrashGlyph(vm.accent) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NColors.bg)
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(e.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -maxSwipePx * 0.6f) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    vm.deleteById(e.id)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val next = (offsetX.value + dragAmount).coerceIn(-maxSwipePx, 0f)
                                offsetX.snapTo(next)
                            }
                        },
                    )
                }
                .pointerInput(e.id) {
                    detectTapGestures(onTap = { vm.openEvent(e.id) })
                }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(e.color, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = NColors.textPrimary, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(CalendarFormats.timeLabelFor(e), color = NColors.textMuted, fontFamily = NFonts.Mono, fontSize = 12.sp)
                    if (!e.location.isNullOrBlank()) {
                        Text(" · ${e.location}", color = NColors.textFaint, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
    if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.divider))
}
