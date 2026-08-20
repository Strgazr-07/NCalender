package com.ncalendar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.RepeatRule
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import java.time.LocalDate
import java.time.LocalTime

/**
 * Nothing-styled replacements for the stock Android date/time picker dialogs.
 * Both render as bottom sheets over a scrim, matching the app's sheet language.
 */

/** Readable text/glyph color for content sitting ON an accent-filled surface. The accent is
 *  user-selectable and includes a near-white option, against which a hardcoded white (the
 *  previous behavior on the selected-date disc and the confirm button) was invisible. */
private fun onAccent(accent: Color): Color =
    if (accent.luminance() > 0.6f) Color(0xFF15150F) else Color.White

@Composable
private fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(NColors.bgElevated, RoundedCornerShape(22.dp, 22.dp, 0.dp, 0.dp))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp).height(4.dp)
                    .background(NColors.surfaceInset, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(NColors.accent, size = 5.dp)
                Spacer(Modifier.width(8.dp))
                MonoLabel(title, color = NColors.textDim)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------- time picker

private val WHEEL_ITEM_H = 44.dp
private const val WHEEL_VISIBLE = 5

@Composable
fun NTimePickerSheet(
    title: String,
    initial: LocalTime,
    ndot: Boolean,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    // 12-hour wheels with an explicit AM/PM column — a bare 24h wheel made it
    // too easy to schedule "10:50" in the morning while meaning the evening.
    var hour12 by remember { mutableStateOf(((initial.hour + 11) % 12) + 1) }
    var minute by remember { mutableStateOf(initial.minute) }
    var pmIndex by remember { mutableStateOf(if (initial.hour >= 12) 1 else 0) }

    PickerSheet(title, onDismiss) {
        Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_H * WHEEL_VISIBLE)) {
            // Center highlight band behind the selected row.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(WHEEL_ITEM_H)
                    .background(NColors.surfaceHi, RoundedCornerShape(12.dp))
                    .border(1.dp, NColors.borderStrong, RoundedCornerShape(12.dp)),
            )
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelColumn(
                    labels = (1..12).map { CalendarFormats.pad(it) },
                    initialIndex = hour12 - 1,
                    ndot = ndot,
                    onSelected = { hour12 = it + 1 },
                )
                Text(
                    ":",
                    color = NColors.textDim,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                WheelColumn(
                    labels = (0..59).map { CalendarFormats.pad(it) },
                    initialIndex = initial.minute,
                    ndot = ndot,
                    onSelected = { minute = it },
                )
                Spacer(Modifier.width(14.dp))
                WheelColumn(
                    labels = listOf("AM", "PM"),
                    initialIndex = pmIndex,
                    ndot = false,
                    onSelected = { pmIndex = it },
                    width = 58.dp,
                    textSize = 18.sp,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SheetButtons(
            accent = accent,
            confirmLabel = "Set time",
            onDismiss = onDismiss,
            onConfirm = {
                val hour24 = (hour12 % 12) + if (pmIndex == 1) 12 else 0
                onConfirm(LocalTime.of(hour24, minute))
            },
        )
    }
}

// ------------------------------------------------------------- reminder picker

/** Custom "remind me N minutes/hours/days/weeks before" wheel. Emits minutes-before.
 *  [onClear], when supplied, adds a "No reminder" action — used where the reminder is an
 *  optional standing setting (a calendar's default) rather than an item being added to a list. */
@Composable
fun NReminderPickerSheet(
    accent: Color,
    ndot: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    title: String = "Custom reminder",
    confirmLabel: String = "Add reminder",
    onClear: (() -> Unit)? = null,
) {
    val units = listOf("Minutes" to 1, "Hours" to 60, "Days" to 1440, "Weeks" to 10080)
    var amount by remember { mutableStateOf(10) }
    var unitIndex by remember { mutableStateOf(0) }

    PickerSheet(title, onDismiss) {
        Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_H * WHEEL_VISIBLE)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(WHEEL_ITEM_H)
                    .background(NColors.surfaceHi, RoundedCornerShape(12.dp))
                    .border(1.dp, NColors.borderStrong, RoundedCornerShape(12.dp)),
            )
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelColumn(
                    labels = (1..60).map { it.toString() },
                    initialIndex = amount - 1,
                    ndot = ndot,
                    onSelected = { amount = it + 1 },
                )
                Spacer(Modifier.width(14.dp))
                WheelColumn(
                    labels = units.map { it.first },
                    initialIndex = unitIndex,
                    ndot = false,
                    onSelected = { unitIndex = it },
                    width = 108.dp,
                    textSize = 17.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "before the event starts",
            color = NColors.textFaint,
            fontFamily = NFonts.Mono,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        SheetButtons(
            accent = accent,
            confirmLabel = confirmLabel,
            onDismiss = onDismiss,
            onConfirm = { onConfirm(amount * units[unitIndex].second) },
        )
        if (onClear != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No reminder",
                color = NColors.textDim,
                fontFamily = NFonts.Mono,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClear).padding(vertical = 10.dp),
            )
        }
    }
}

// --------------------------------------------------------- custom recurrence

@Composable
fun NCustomRecurrenceSheet(
    initialRule: RepeatRule,
    initialInterval: Int,
    initialByDays: Set<Int>,
    initialUntil: LocalDate?,
    initialCount: Int?,
    weekStart: Int,
    today: LocalDate,
    accent: Color,
    ndot: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RepeatRule, Int, Set<Int>, LocalDate?, Int?) -> Unit,
) {
    var rule by remember { mutableStateOf(if (initialRule == RepeatRule.NONE) RepeatRule.WEEKLY else initialRule) }
    var interval by remember { mutableStateOf(initialInterval.coerceAtLeast(1)) }
    var byDays by remember { mutableStateOf(initialByDays.ifEmpty { setOf(today.dayOfWeek.value) }) }
    var endMode by remember {
        mutableStateOf(
            when {
                initialUntil != null -> "Until"
                initialCount != null -> "After"
                else -> "Never"
            }
        )
    }
    var until by remember { mutableStateOf(initialUntil ?: today.plusMonths(1)) }
    var count by remember { mutableStateOf(initialCount ?: 10) }
    var untilPickerOpen by remember { mutableStateOf(false) }

    PickerSheet("Custom repeat", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Day" to RepeatRule.DAILY, "Week" to RepeatRule.WEEKLY, "Month" to RepeatRule.MONTHLY, "Year" to RepeatRule.YEARLY)
                .forEach { (label, value) ->
                    Pill(label, selected = rule == value, accentColor = accent, onClick = { rule = value })
                }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Every", color = NColors.textSecondary, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            WheelColumn(
                labels = (1..30).map { it.toString() },
                initialIndex = interval - 1,
                ndot = ndot,
                onSelected = { interval = it + 1 },
                width = 62.dp,
                textSize = 22.sp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when (rule) {
                    RepeatRule.DAILY -> if (interval == 1) "day" else "days"
                    RepeatRule.WEEKLY -> if (interval == 1) "week" else "weeks"
                    RepeatRule.MONTHLY -> if (interval == 1) "month" else "months"
                    RepeatRule.YEARLY -> if (interval == 1) "year" else "years"
                    else -> "weeks"
                },
                color = NColors.textSecondary,
                fontSize = 15.sp,
            )
        }
        if (rule == RepeatRule.WEEKLY) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CalendarFormats.dowShortLabels(weekStart).forEachIndexed { idx, label ->
                    val iso = ((weekStart + idx + 6) % 7) + 1
                    Pill(
                        text = label,
                        selected = iso in byDays,
                        accentColor = accent,
                        onClick = {
                            val next = if (iso in byDays) byDays - iso else byDays + iso
                            byDays = next.ifEmpty { setOf(iso) }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Never", "Until", "After").forEach { label ->
                Pill(label, selected = endMode == label, accentColor = accent, onClick = { endMode = label })
            }
        }
        if (endMode == "Until") {
            Spacer(Modifier.height(10.dp))
            Text(
                CalendarFormats.fmtDateShort(until),
                color = NColors.textPrimary,
                fontFamily = NFonts.Mono,
                fontSize = 15.sp,
                modifier = Modifier
                    .background(NColors.surfaceHi, RoundedCornerShape(9.dp))
                    .clickable { untilPickerOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else if (endMode == "After") {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WheelColumn(
                    labels = (1..99).map { it.toString() },
                    initialIndex = count - 1,
                    ndot = ndot,
                    onSelected = { count = it + 1 },
                    width = 68.dp,
                    textSize = 22.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text("times", color = NColors.textSecondary, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        SheetButtons(
            accent = accent,
            confirmLabel = "Set repeat",
            onDismiss = onDismiss,
            onConfirm = {
                onConfirm(
                    rule,
                    interval,
                    if (rule == RepeatRule.WEEKLY) byDays else emptySet(),
                    if (endMode == "Until") until else null,
                    if (endMode == "After") count else null,
                )
            },
        )
    }

    if (untilPickerOpen) {
        NDatePickerSheet(
            title = "Repeat until",
            initial = until,
            weekStart = weekStart,
            today = today,
            accent = accent,
            onDismiss = { untilPickerOpen = false },
            onConfirm = { until = it; untilPickerOpen = false },
        )
    }
}

/** A snapping wheel: 5 visible rows, the centered one is selected (reported by index). */
@Composable
fun WheelColumn(
    labels: List<String>,
    initialIndex: Int,
    ndot: Boolean,
    onSelected: (Int) -> Unit,
    width: androidx.compose.ui.unit.Dp = 72.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 26.sp,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, labels.lastIndex))
    val itemPx = with(LocalDensity.current) { WHEEL_ITEM_H.toPx() }
    val haptic = LocalHapticFeedback.current

    val selectedIndex by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            idx.coerceIn(0, labels.lastIndex)
        }
    }
    LaunchedEffect(selectedIndex) {
        onSelected(selectedIndex)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(vertical = WHEEL_ITEM_H * (WHEEL_VISIBLE / 2)),
        modifier = Modifier.width(width).height(WHEEL_ITEM_H * WHEEL_VISIBLE),
    ) {
        items(labels.size) { i ->
            val sel = i == selectedIndex
            Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_H), contentAlignment = Alignment.Center) {
                Text(
                    labels[i],
                    color = if (sel) NColors.textPrimary else NColors.textFainter,
                    fontFamily = NFonts.numeral(ndot),
                    fontSize = if (sel) textSize else textSize * 0.78f,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- date picker

@Composable
fun NDatePickerSheet(
    title: String,
    initial: LocalDate,
    weekStart: Int,
    today: LocalDate,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var anchor by remember { mutableStateOf(initial.withDayOfMonth(1)) }

    PickerSheet(title, onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton("‹", onClick = { anchor = anchor.minusMonths(1) }, size = 34.dp)
            Text(
                "${CalendarFormats.MON_FULL[anchor.monthValue - 1]} ${anchor.year}",
                color = NColors.textPrimary,
                fontFamily = NFonts.Mono,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
            )
            RoundIconButton("›", onClick = { anchor = anchor.plusMonths(1) }, size = 34.dp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            CalendarFormats.dowShortLabels(weekStart).forEach { l ->
                Text(
                    l,
                    color = NColors.textFaint,
                    fontFamily = NFonts.Mono,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val cells = CalendarFormats.monthGridDates(anchor, weekStart)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val inMonth = d.monthValue == anchor.monthValue
                    val isSel = d == initial
                    val isToday = d == today
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(3.dp)
                            .background(if (isSel) accent else Color.Transparent, CircleShape)
                            .border(1.dp, if (isToday && !isSel) NColors.borderStrong else Color.Transparent, CircleShape)
                            .clickable { onConfirm(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            color = when {
                                isSel -> onAccent(accent)
                                isToday -> accent
                                inMonth -> NColors.textPrimary
                                else -> NColors.textGhostDeep
                            },
                            fontSize = 15.sp,
                            fontWeight = if (isSel || isToday) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetButtons(
    accent: Color,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row {
        Box(
            Modifier
                .height(50.dp)
                .background(NColors.surfaceHi, RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { MonoLabel("Cancel", color = NColors.textSecondary) }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(50.dp)
                .background(accent, RoundedCornerShape(14.dp))
                .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) { MonoLabel(confirmLabel, color = onAccent(accent)) }
    }
}

// ---------------------------------------------------------------- recurring-event scope

/**
 * Asks whether an edit/delete/drag on a recurring event applies to just that occurrence or the
 * whole series. Shown before the action actually runs — see CalendarViewModel.PendingScopeAction.
 */
@Composable
fun NRecurringScopeSheet(
    accent: Color,
    isDelete: Boolean,
    onDismiss: () -> Unit,
    onThisEvent: () -> Unit,
    onAllEvents: () -> Unit,
) {
    PickerSheet(if (isDelete) "Delete event" else "Repeating event", onDismiss) {
        Text(
            if (isDelete) "This event repeats. What do you want to delete?" else "This event repeats. What do you want to change?",
            color = NColors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScopeOption(
                title = "This event",
                subtitle = if (isDelete) "Only this occurrence is deleted" else "Only this occurrence is changed",
                onClick = onThisEvent,
            )
            ScopeOption(
                title = "All events",
                subtitle = if (isDelete) "The entire series is deleted" else "Every occurrence in the series is changed",
                accent = accent,
                onClick = onAllEvents,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Cancel",
            color = NColors.textDim,
            fontFamily = NFonts.Mono,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss)
                .padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun ScopeOption(title: String, subtitle: String, onClick: () -> Unit, accent: Color? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(accent?.copy(alpha = 0.12f) ?: NColors.surfaceHi, RoundedCornerShape(14.dp))
            .border(1.dp, accent ?: NColors.borderStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, color = NColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = NColors.textFaint, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}
