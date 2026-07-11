package com.ncalendar.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.ui.components.Dot
import com.ncalendar.app.ui.components.GearGlyph
import com.ncalendar.app.ui.components.ListGlyph
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.PlusFab
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.components.SearchGlyph
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel
import com.ncalendar.app.viewmodel.Screen
import com.ncalendar.app.viewmodel.ViewMode
import java.time.LocalDateTime

/** Shared horizontal screen inset — a single rhythm keeps the layout symmetric. */
val ScreenPad = 24.dp

@Composable
fun AppScreen(vm: CalendarViewModel) {
    val events by vm.events.collectAsState()
    val now by vm.now.collectAsState()
    val state = vm.state
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(Modifier.fillMaxSize().background(NColors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopUtilityRow(vm)
            HeaderBar(vm)
            NowNextSpine(vm, events, now)
            ZoomControl(vm)
            Spacer(Modifier.height(10.dp))

            // Horizontal swipe changes the period (month / week / day).
            val swipeable = state.view != ViewMode.AGENDA
            val swipeModifier = if (swipeable) {
                Modifier.pointerInput(state.view) {
                    var totalDx = 0f
                    val threshold = 56.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f },
                        onDragEnd = {
                            if (totalDx <= -threshold) {
                                vm.goNext(); haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } else if (totalDx >= threshold) {
                                vm.goPrev(); haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            }
                        },
                    ) { change, dragAmount -> totalDx += dragAmount }
                }
            } else Modifier

            Box(Modifier.weight(1f).clipToBounds().then(swipeModifier)) {
                // The visible period, so prev/next navigation animates: the month
                // for MONTH, the week's first day for WEEK, the day for DAY.
                val periodKey = when (state.view) {
                    ViewMode.MONTH -> state.anchor.withDayOfMonth(1)
                    ViewMode.WEEK -> CalendarFormats.weekDates(state.selDay, vm.weekStart).first()
                    ViewMode.DAY -> state.selDay
                    ViewMode.AGENDA -> java.time.LocalDate.MIN
                }
                AnimatedContent(
                    targetState = state.view to periodKey,
                    transitionSpec = {
                        val (fromView, fromKey) = initialState
                        val (toView, toKey) = targetState
                        // MONTH -> WEEK -> DAY is a zoom-in; AGENDA sits outside the zoom axis.
                        fun depth(v: ViewMode) = when (v) {
                            ViewMode.MONTH -> 0; ViewMode.WEEK -> 1; ViewMode.DAY -> 2; ViewMode.AGENDA -> -1
                        }
                        when {
                            fromView != toView && (depth(fromView) < 0 || depth(toView) < 0) ->
                                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                            fromView != toView -> {
                                val zoomingIn = depth(toView) > depth(fromView)
                                val enterFrom = if (zoomingIn) 0.94f else 1.05f
                                val exitTo = if (zoomingIn) 1.05f else 0.94f
                                (fadeIn(tween(230)) + scaleIn(initialScale = enterFrom, animationSpec = tween(230))) togetherWith
                                    (fadeOut(tween(170)) + scaleOut(targetScale = exitTo, animationSpec = tween(170)))
                            }
                            else -> {
                                val dir = if (toKey > fromKey) 1 else -1
                                (slideInHorizontally(tween(260)) { dir * it / 3 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(220)) { -dir * it / 3 } + fadeOut(tween(180)))
                            }
                        }
                    },
                    label = "calendarBody",
                ) { (view, _) ->
                    when (view) {
                        ViewMode.MONTH -> MonthView(vm, events)
                        ViewMode.WEEK -> WeekView(vm, events)
                        ViewMode.DAY -> DayView(vm, events)
                        ViewMode.AGENDA -> AgendaView(vm, events)
                    }
                }
            }
        }

        PlusFab(
            onClick = { vm.openNewEvent(state.selDay) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = ScreenPad, bottom = 22.dp),
        )

        if (state.pickerOpen) MonthYearPickerSheet(vm)
        if (state.menuEventId != null) LongPressMenu(vm, events)
    }
}

@Composable
private fun TopUtilityRow(vm: CalendarViewModel) {
    val state = vm.state
    val showBackToday = when (state.view) {
        ViewMode.MONTH -> state.anchor.year != vm.today.year || state.anchor.monthValue != vm.today.monthValue
        else -> state.selDay != vm.today
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, start = ScreenPad - 6.dp, end = ScreenPad - 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Box(Modifier.clickable { vm.go(Screen.SEARCH) }.padding(8.dp)) {
                SearchGlyph(size = 20.dp)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.clickable { vm.go(Screen.SETTINGS) }.padding(8.dp)) {
                GearGlyph(size = 21.dp)
            }
        }
        AnimatedVisibility(
            visible = showBackToday,
            enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(150)) + shrinkHorizontally(tween(180)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(NColors.surfaceAlt, RoundedCornerShape(20.dp))
                    .border(1.dp, NColors.borderStrong, RoundedCornerShape(20.dp))
                    .clickable { vm.goToday() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Dot(vm.accent, size = 6.dp)
                Spacer(Modifier.width(8.dp))
                MonoLabel("Back to today", color = NColors.textSecondary)
            }
        }
    }
}

@Composable
private fun HeaderBar(vm: CalendarViewModel) {
    val state = vm.state
    val label: String
    val sub: String
    when (state.view) {
        ViewMode.MONTH -> {
            label = CalendarFormats.MON_FULL[state.anchor.monthValue - 1]
            sub = state.anchor.year.toString()
        }
        ViewMode.AGENDA -> { label = "Agenda"; sub = "Upcoming" }
        ViewMode.DAY -> {
            val d = state.selDay
            label = "${CalendarFormats.DOW[CalendarFormats.dowIndex(d)]} ${d.dayOfMonth}"
            sub = "${CalendarFormats.MON_FULL[d.monthValue - 1]} ${d.year}"
        }
        ViewMode.WEEK -> {
            val wd = CalendarFormats.weekDates(state.selDay, vm.weekStart)
            val st = wd.first(); val en = wd.last()
            label = if (st.monthValue == en.monthValue)
                "${CalendarFormats.MON[st.monthValue - 1]} ${st.dayOfMonth}–${en.dayOfMonth}"
            else
                "${CalendarFormats.MON[st.monthValue - 1]} ${st.dayOfMonth}–${CalendarFormats.MON[en.monthValue - 1]} ${en.dayOfMonth}"
            sub = st.year.toString()
        }
    }
    Column(Modifier.padding(top = 10.dp, start = ScreenPad, end = ScreenPad, bottom = 14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { vm.openPicker() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Crossfade the title when the period changes so prev/next feels alive.
                AnimatedContent(
                    targetState = sub to label,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(220, delayMillis = 40)) { it / 8 }) togetherWith
                            fadeOut(tween(120))
                    },
                    label = "headerTitle",
                ) { (s, l) ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            s,
                            color = NColors.textMuted,
                            fontFamily = NFonts.Mono,
                            fontSize = 13.sp,
                            letterSpacing = 4.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            text = l,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 40.sp, stepSize = 1.sp),
                            style = TextStyle(
                                color = NColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = NFonts.display(vm.ndot),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            ),
                        )
                    }
                }
            }
            RoundIconButton("‹", onClick = { vm.goPrev() }, modifier = Modifier.align(Alignment.CenterStart))
            RoundIconButton("›", onClick = { vm.goNext() }, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun NowNextSpine(vm: CalendarViewModel, events: List<EventItem>, now: LocalDateTime) {
    val ongoing = vm.ongoingEvent(events, now)
    val next = vm.nextEvent(events, now)

    val data = when {
        ongoing != null -> {
            val leftMin = java.time.Duration.between(now, ongoing.end).toMinutes()
            SpineData("Happening now", ongoing.title, "$leftMin min left", vm.accent, vm.accent.copy(alpha = 0.10f), vm.accent.copy(alpha = 0.4f), vm.accent) { vm.openEvent(ongoing.id) }
        }
        next != null -> {
            val mins = java.time.Duration.between(now, next.start).toMinutes()
            val cd = if (mins < 60) "in $mins min" else "in ${mins / 60}h${if (mins % 60 != 0L) " ${mins % 60}m" else ""}"
            val pre = if (next.startDate == vm.today) "" else " · ${CalendarFormats.fmtDateFull(next.startDate, vm.today)}"
            SpineData("Up next$pre", next.title, cd, vm.accent, NColors.surfaceAlt, NColors.borderStrong, NColors.textDim) { vm.openEvent(next.id) }
        }
        else -> SpineData("All clear", "Nothing left today", "", NColors.textFainter, NColors.surfaceAlt, NColors.border, NColors.textFainter) {}
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = ScreenPad)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .background(data.bg, RoundedCornerShape(18.dp))
            .border(1.dp, data.border, RoundedCornerShape(18.dp))
            .clickable(onClick = data.onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Dot(data.dotColor, size = 9.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MonoLabel(data.kind, color = data.kindColor)
            Spacer(Modifier.height(5.dp))
            Text(data.title, color = NColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (data.count.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(data.count, color = vm.accent, fontSize = 16.sp, fontFamily = NFonts.numeral(vm.ndot), fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class SpineData(
    val kind: String,
    val title: String,
    val count: String,
    val dotColor: Color,
    val bg: Color,
    val border: Color,
    val kindColor: Color,
    val onClick: () -> Unit,
)

@Composable
private fun ZoomControl(vm: CalendarViewModel) {
    val state = vm.state
    val stops = listOf("MONTH" to ViewMode.MONTH, "WEEK" to ViewMode.WEEK, "DAY" to ViewMode.DAY)
    val activeIdx = stops.indexOfFirst { it.second == state.view }

    Row(
        Modifier.padding(horizontal = ScreenPad).padding(bottom = 8.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(48.dp)
                .background(NColors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, NColors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(4.dp),
        ) {
            val segW = maxWidth / stops.size
            // Sliding white knob behind the active segment.
            if (activeIdx >= 0) {
                val knobLeft by animateDpAsState(targetValue = segW * activeIdx, label = "knob")
                Box(
                    Modifier
                        .offset(x = knobLeft)
                        .width(segW)
                        .fillMaxHeight()
                        .background(NColors.inverseBg, RoundedCornerShape(10.dp)),
                )
            }
            Row(Modifier.fillMaxSize()) {
                stops.forEachIndexed { i, (label, mode) ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { vm.setView(mode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (activeIdx == i) NColors.onInverse else NColors.textDim,
                            fontFamily = NFonts.Mono,
                            fontSize = 12.sp,
                            fontWeight = if (activeIdx == i) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 1.5.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val isAgenda = state.view == ViewMode.AGENDA
        Box(
            Modifier
                .size(48.dp)
                .background(if (isAgenda) NColors.textPrimary else NColors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, NColors.borderSubtle, RoundedCornerShape(14.dp))
                .clickable { vm.toggleAgenda() },
            contentAlignment = Alignment.Center,
        ) {
            ListGlyph(if (isAgenda) NColors.onInverse else NColors.textDim, size = 20.dp)
        }
    }
}
