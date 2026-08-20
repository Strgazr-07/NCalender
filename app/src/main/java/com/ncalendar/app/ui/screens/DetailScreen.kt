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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.ics.IcsExportManager
import com.ncalendar.app.data.RepeatRule
import com.ncalendar.app.ui.components.BellGlyph
import com.ncalendar.app.ui.components.LocationGlyph
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.NReminderPickerSheet
import com.ncalendar.app.ui.components.RepeatGlyph
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.components.TrashGlyph
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel

@Composable
fun DetailScreen(vm: CalendarViewModel) {
    val context = LocalContext.current
    val events by vm.events.collectAsState()
    val calendars by vm.calendars.collectAsState()
    val e = events.find { it.id == vm.state.selId }
    if (e == null) {
        vm.closeDetail()
        return
    }
    // Events on a read-only calendar (a synced Birthdays or Holidays feed) can't be edited at
    // all, so the normal editor route to setting a reminder is unavailable — they get a
    // dedicated app-side reminder action instead.
    val readOnly = calendars.firstOrNull { it.id == e.calendarId }?.isWritable == false
    var reminderSheetOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(NColors.bg).statusBarsPadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(186.dp)
                .background(e.color.copy(alpha = 0.16f))
                .padding(start = ScreenPad, end = ScreenPad, top = 14.dp),
        ) {
            RoundIconButton("‹", onClick = { vm.closeDetail() })
            Column(Modifier.align(Alignment.BottomStart).padding(bottom = 20.dp)) {
                MonoLabel(e.calendarName, color = e.color.copy(alpha = 0.9f))
                Spacer(Modifier.height(8.dp))
                Text(e.title, color = NColors.textPrimary, fontSize = com.ncalendar.app.ui.theme.NType.H2, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPad)) {
            DetailRow {
                com.ncalendar.app.ui.components.CalendarGlyph()
                Spacer(Modifier.width(16.dp))
                Column {
                    val sd = e.start
                    Text(
                        "${CalendarFormats.DOW[CalendarFormats.dowIndex(e.startDate)]}, ${CalendarFormats.MON[sd.monthValue - 1]} ${sd.dayOfMonth}, ${sd.year}",
                        color = NColors.textPrimary, fontFamily = NFonts.numeral(vm.ndot), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(CalendarFormats.timeLabelFor(e), color = NColors.textMuted, fontFamily = NFonts.numeral(vm.ndot), fontSize = 14.sp)
                }
            }
            if (!e.location.isNullOrBlank()) {
                DetailRow {
                    LocationGlyph()
                    Spacer(Modifier.width(16.dp))
                    Text(e.location, color = NColors.textSecondary, fontSize = 16.sp)
                }
            }
            DetailRow {
                BellGlyph()
                Spacer(Modifier.width(16.dp))
                Column {
                    val reminders = e.reminders.ifEmpty { listOf(0) }
                    reminders.forEach { m ->
                        Text(CalendarFormats.reminderLabel(m), color = NColors.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            if (e.repeat != RepeatRule.NONE) {
                DetailRow {
                    RepeatGlyph()
                    Spacer(Modifier.width(16.dp))
                    Text(
                        CalendarFormats.repeatSummary(e.repeat, e.repeatInterval, e.repeatByDays, e.repeatEndDate, e.repeatEndCount),
                        color = NColors.textSecondary,
                        fontSize = 16.sp,
                    )
                }
            }
            if (!e.notes.isNullOrBlank()) {
                Column(Modifier.padding(vertical = 18.dp)) {
                    MonoLabel("Notes", color = NColors.textFaint)
                    Spacer(Modifier.height(10.dp))
                    Text(e.notes, color = NColors.dimNum, fontSize = 15.sp, lineHeight = 23.sp)
                }
            }
            Spacer(Modifier.height(90.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(NColors.bg)
                .navigationBarsPadding()
                .padding(horizontal = ScreenPad, vertical = 16.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .background(NColors.inverseBg, RoundedCornerShape(14.dp))
                    .clickable { if (readOnly) reminderSheetOpen = true else vm.openEdit(events) },
                contentAlignment = Alignment.Center,
            ) {
                MonoLabel(if (readOnly) "Remind me" else "Edit", color = NColors.onInverse)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(56.dp)
                    .height(50.dp)
                    .background(NColors.surfaceAlt, RoundedCornerShape(14.dp))
                    .clickable { vm.dupById(events, e.id) },
                contentAlignment = Alignment.Center,
            ) {
                MonoLabel("Copy", color = NColors.textSecondary, size = 10.sp)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(56.dp)
                    .height(50.dp)
                    .background(NColors.surfaceAlt, RoundedCornerShape(14.dp))
                    .clickable { IcsExportManager.shareEvents(context, listOf(e), "ncalendar-event.ics") },
                contentAlignment = Alignment.Center,
            ) {
                MonoLabel("Share", color = NColors.textSecondary, size = 10.sp)
            }
            if (!readOnly) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .width(56.dp)
                        .height(50.dp)
                        .background(NColors.surfaceAlt, RoundedCornerShape(14.dp))
                        .clickable { vm.deleteEvent() },
                    contentAlignment = Alignment.Center,
                ) {
                    TrashGlyph(vm.accent)
                }
            }
        }
    }

    if (reminderSheetOpen) {
        NReminderPickerSheet(
            accent = vm.accent,
            ndot = vm.ndot,
            onDismiss = { reminderSheetOpen = false },
            onConfirm = { minutes ->
                vm.setEventReminders(e.id, listOf(minutes))
                reminderSheetOpen = false
            },
            title = "Remind me about this",
            confirmLabel = "Set reminder",
            onClear = {
                vm.setEventReminders(e.id, emptyList())
                reminderSheetOpen = false
            },
        )
    }
}

@Composable
private fun DetailRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
    Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.border))
}
