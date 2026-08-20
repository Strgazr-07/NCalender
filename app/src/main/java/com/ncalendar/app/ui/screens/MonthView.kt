package com.ncalendar.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

    val selD = state.selDay

    // The day-summary card is the ONLY feedback a tap on the grid produces, so it has to be
    // on screen — previously the grid claimed a fixed aspectRatio height and the card, as an
    // unweighted sibling, got whatever was left. On a shorter phone that was nothing: it
    // collapsed to zero height, and tapping a date genuinely appeared to do nothing at all.
    // Now the GRID is the flexible one and the card is measured first at its natural height.
    // Below a threshold where even a squeezed grid stops being usable, the whole thing scrolls
    // instead of silently dropping the card.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = ScreenPad),
    ) {
        val roomy = maxHeight >= COMPACT_HEIGHT_THRESHOLD
        val body: @Composable ColumnScope.() -> Unit = {
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

            val gridModifier = if (roomy) {
                Modifier.fillMaxWidth().weight(1f).heightIn(min = MIN_GRID_HEIGHT)
            } else {
                // Scrolling layout: no weight to take, so fall back to the natural proportions.
                Modifier.fillMaxWidth().aspectRatio(7f / 6f)
            }
            Column(gridModifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

            Spacer(Modifier.height(12.dp))
            DaySummaryCard(vm, selD, dayIndex[selD].orEmpty())
            Spacer(Modifier.height(16.dp))
        }

        if (roomy) {
            Column(Modifier.fillMaxSize(), content = body)
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = body)
        }
    }
}

/** Below this much vertical room the grid can't shrink further without its day numbers and
 *  event dots colliding, so the layout switches from "everything pinned" to scrolling. */
private val COMPACT_HEIGHT_THRESHOLD = 420.dp
private val MIN_GRID_HEIGHT = 240.dp

@Composable
private fun DaySummaryCard(vm: CalendarViewModel, day: LocalDate, events: List<EventItem>) {
    val dateLabel = "${CalendarFormats.DOW[CalendarFormats.dowIndex(day)]}, ${CalendarFormats.MON[day.monthValue - 1]} ${day.dayOfMonth}"
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            // Intrinsic height with a floor rather than a hard 132.dp: as a fixed-height,
            // unweighted child this card used to be the thing that got squeezed out of the
            // layout entirely on shorter screens.
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(NColors.surface, shape)
            .border(1.dp, NColors.border, shape)
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
                // Always present — it used to be hidden on empty days, i.e. exactly when
                // someone poking at the app to see whether tapping does anything would look
                // for it. This chevron is the app's only cue that the card opens the day.
                Spacer(Modifier.width(6.dp))
                Text("›", color = if (events.isEmpty()) NColors.textDim else vm.accent, fontSize = 17.sp)
                // Replaces the floating action button in month view (see AppScreen), which
                // used to sit on top of this card.
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(NColors.surfaceHi, CircleShape)
                        .clickable { vm.openNewEvent(day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = NColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
        } else {
            // The header's "+" already covers adding here, so this space explains the two
            // gestures the grid supports instead of repeating that button.
            Spacer(Modifier.height(12.dp))
            MonoLabel("Tap a date to select · hold to add", color = NColors.textGhost, size = 10.sp)
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)

    // The old selection treatment was surfaceSel (#141412) on a pure-black background — about
    // a 5% luminance step, plus a 1dp near-invisible hairline. Combined with the double-tap
    // delay below, a tap looked like it did nothing. Selection now uses the user's accent, and
    // "selected AND today" gets the filled disc used by the date picker and the widgets.
    val selectionFill by animateColorAsState(
        when {
            isSel && isToday -> vm.accent
            isSel -> vm.accent.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        label = "cellFill",
    )
    val selectionRing by animateColorAsState(
        if (isSel && !isToday) vm.accent.copy(alpha = 0.55f) else Color.Transparent,
        label = "cellRing",
    )

    Column(
        Modifier
            .fillMaxSize()
            // clip before background/clickable so the ripple follows the rounded corners
            // instead of flashing as a rectangle over them.
            .clip(shape)
            .background(selectionFill, shape)
            .border(1.5.dp, selectionRing, shape)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.selectDay(date)
                },
                // A genuinely useful third action, and it matches the long-press vocabulary
                // DayView and AgendaView already use.
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.openNewEvent(date)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = when {
                isSel && isToday -> NColors.onInverse
                isToday -> vm.accent
                inMonth -> NColors.textPrimary
                else -> NColors.textGhost
            },
            fontSize = 19.sp,
            fontWeight = when {
                isToday || isSel -> FontWeight.Bold
                inMonth -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            dayEvents.forEach { e ->
                val dim = !inMonth
                val ink = when {
                    // On the filled "today + selected" disc, accent-on-accent would vanish.
                    isSel && isToday -> NColors.onInverse
                    dim -> vm.accent.copy(alpha = 0.35f)
                    else -> vm.accent
                }
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
