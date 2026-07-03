package com.ncalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.ncalendar.app.ui.components.Dot
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.viewmodel.CalendarViewModel

@Composable
fun MonthYearPickerSheet(vm: CalendarViewModel) {
    val state = vm.state
    val curY = vm.today.year
    val curM = vm.today.monthValue - 1
    val aY = state.anchor.year
    val aM = state.anchor.monthValue - 1

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { vm.closePicker() },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(NColors.bgElevated, RoundedCornerShape(22.dp, 22.dp, 0.dp, 0.dp))
                .navigationBarsPadding()
                .padding(horizontal = ScreenPad, vertical = 12.dp)
                .padding(bottom = 20.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                    .background(NColors.surfaceInset, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton("‹", onClick = { vm.shiftPickerYear(-1) }, size = 34.dp)
                // Years also respond to horizontal swipes, not just the arrows.
                val yearSwipe = Modifier.pointerInput(Unit) {
                    var totalDx = 0f
                    val threshold = 48.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f },
                        onDragEnd = {
                            if (totalDx <= -threshold) vm.shiftPickerYear(1)
                            else if (totalDx >= threshold) vm.shiftPickerYear(-1)
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        totalDx += dragAmount
                    }
                }
                Row(Modifier.weight(1f).then(yearSwipe), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    (-2..2).forEach { off ->
                        val y = state.pickerYear + off
                        val sel = y == state.pickerYear
                        Text(
                            y.toString(),
                            color = if (sel) NColors.onInverse else NColors.textDim,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .background(if (sel) NColors.textPrimary else Color.Transparent, RoundedCornerShape(15.dp))
                                .clickable { vm.pickYear(y) }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
                RoundIconButton("›", onClick = { vm.shiftPickerYear(1) }, size = 34.dp)
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(200.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(12) { m ->
                    val isCur = state.pickerYear == curY && m == curM
                    val isSel = state.pickerYear == aY && m == aM
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(if (isSel) NColors.surfaceSel2 else NColors.surface, RoundedCornerShape(12.dp))
                            .clickable { vm.selectMonth(m) },
                        contentAlignment = Alignment.Center,
                    ) {
                        MonoLabel(
                            CalendarFormats.MON[m],
                            color = if (isSel) NColors.textPrimary else if (isCur) vm.accent else NColors.dimNum,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally)
                    .background(NColors.surfaceHi, RoundedCornerShape(20.dp))
                    .clickable { vm.pickerJumpToday() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(vm.accent, size = 5.dp)
                Spacer(Modifier.width(8.dp))
                MonoLabel("Jump to today", color = NColors.textSecondary)
            }
        }
    }
}
