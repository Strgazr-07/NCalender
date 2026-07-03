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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import java.time.LocalDate
import java.time.LocalTime

/**
 * Nothing-styled replacements for the stock Android date/time picker dialogs.
 * Both render as bottom sheets over a scrim, matching the app's sheet language.
 */

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
                Dot(Color(0xFFD71921), size = 5.dp)
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
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }

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
                    values = (0..23).toList(),
                    initial = initial.hour,
                    ndot = ndot,
                    onSelected = { hour = it },
                )
                Text(
                    ":",
                    color = NColors.textDim,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                WheelColumn(
                    values = (0..59).toList(),
                    initial = initial.minute,
                    ndot = ndot,
                    onSelected = { minute = it },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SheetButtons(
            accent = accent,
            confirmLabel = "Set time",
            onDismiss = onDismiss,
            onConfirm = { onConfirm(LocalTime.of(hour, minute)) },
        )
    }
}

/** A snapping number wheel: 5 visible rows, the centered one is selected. */
@Composable
private fun WheelColumn(
    values: List<Int>,
    initial: Int,
    ndot: Boolean,
    onSelected: (Int) -> Unit,
) {
    val startIndex = values.indexOf(initial).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val itemPx = with(LocalDensity.current) { WHEEL_ITEM_H.toPx() }
    val haptic = LocalHapticFeedback.current

    val selectedIndex by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            idx.coerceIn(0, values.lastIndex)
        }
    }
    LaunchedEffect(selectedIndex) {
        onSelected(values[selectedIndex])
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(vertical = WHEEL_ITEM_H * (WHEEL_VISIBLE / 2)),
        modifier = Modifier.width(72.dp).height(WHEEL_ITEM_H * WHEEL_VISIBLE),
    ) {
        items(values.size) { i ->
            val sel = i == selectedIndex
            Box(Modifier.fillMaxWidth().height(WHEEL_ITEM_H), contentAlignment = Alignment.Center) {
                Text(
                    CalendarFormats.pad(values[i]),
                    color = if (sel) NColors.textPrimary else NColors.textFainter,
                    fontFamily = NFonts.numeral(ndot),
                    fontSize = if (sel) 26.sp else 20.sp,
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
                                isSel -> Color.White
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
        ) { MonoLabel(confirmLabel, color = Color.White) }
    }
}
