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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.RepeatRule
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.NDatePickerSheet
import com.ncalendar.app.ui.components.NTimePickerSheet
import com.ncalendar.app.ui.components.Pill
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.ui.theme.NType
import com.ncalendar.app.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.LocalTime

private enum class PickerField { START_DATE, END_DATE, START_TIME, END_TIME }

@Composable
fun EditorScreen(vm: CalendarViewModel) {
    val form = vm.state.form ?: return
    val isEditing = vm.state.editId != null
    var picker by remember { mutableStateOf<PickerField?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(NColors.bg).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, start = ScreenPad, end = ScreenPad, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(
                "Cancel", color = NColors.textDim, size = NType.LabelBig,
                modifier = Modifier.clickable { vm.cancelEdit() }.padding(vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            MonoLabel(if (isEditing) "Edit event" else "New event", color = NColors.textSecondary, size = NType.LabelBig)
            Spacer(Modifier.weight(1f))
            MonoLabel(
                "Save", color = vm.accent, size = NType.LabelBig,
                modifier = Modifier.clickable { vm.saveEvent() }.padding(vertical = 4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.border))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = ScreenPad, vertical = 18.dp),
        ) {
            TextField(
                value = form.title,
                onValueChange = { v -> vm.patchForm { it.copy(title = v) } },
                placeholder = { Text("Event title", color = NColors.textGhost, fontSize = 22.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = NColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = NFonts.SpaceGrotesk),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = NColors.borderStrong,
                    unfocusedIndicatorColor = NColors.borderStrong,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            MonoLabel("Calendar", color = NColors.textFaint, modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
            val calendars by vm.calendars.collectAsState()
            val writable = calendars.filter { it.isWritable }.ifEmpty { calendars }
            FlowRow {
                writable.forEach { c ->
                    Pill(
                        text = c.name,
                        selected = form.calendarId == c.id,
                        accentColor = c.color,
                        leadingDot = c.color,
                        onClick = { vm.patchForm { it.copy(calendarId = c.id) } },
                    )
                }
            }

            Box(Modifier.fillMaxWidth().padding(top = 24.dp).height(1.dp).background(NColors.border))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp)
                    .clickable { vm.patchForm { it.copy(allDay = !it.allDay) } },
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("All-day", color = NColors.textSecondary, fontSize = 16.sp)
                AnimatedSwitch(checked = form.allDay, accent = vm.accent)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.border))

            DateTimeRow(
                label = "Starts",
                date = form.startDate,
                time = form.startTime,
                showTime = !form.allDay,
                onDateClick = { picker = PickerField.START_DATE },
                onTimeClick = { picker = PickerField.START_TIME },
            )
            DateTimeRow(
                label = "Ends",
                date = form.endDate,
                time = form.endTime,
                showTime = !form.allDay,
                onDateClick = { picker = PickerField.END_DATE },
                onTimeClick = { picker = PickerField.END_TIME },
            )

            MonoLabel("Repeat", color = NColors.textFaint, modifier = Modifier.padding(top = 20.dp, bottom = 9.dp))
            FlowRow {
                RepeatRule.entries.forEach { r ->
                    Pill(text = r.label, selected = form.repeat == r, onClick = { vm.patchForm { it.copy(repeat = r) } })
                }
            }

            MonoLabel("Reminders", color = NColors.textFaint, modifier = Modifier.padding(top = 20.dp, bottom = 9.dp))
            FlowRow {
                listOf(0 to "At time", 10 to "10 min", 30 to "30 min", 60 to "1 hr", 1440 to "1 day").forEach { (m, label) ->
                    val on = form.reminders.contains(m)
                    Pill(
                        text = label,
                        selected = on,
                        accentColor = vm.accent,
                        onClick = {
                            vm.patchForm { f -> f.copy(reminders = if (on) f.reminders.filterNot { it == m } else f.reminders + m) }
                        },
                    )
                }
            }

            TextField(
                value = form.location,
                onValueChange = { v -> vm.patchForm { it.copy(location = v) } },
                placeholder = { Text("Add location", color = NColors.textGhost, fontSize = 15.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = NColors.textPrimary, fontSize = 15.sp, fontFamily = NFonts.SpaceGrotesk),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = NColors.borderStrong,
                    unfocusedIndicatorColor = NColors.borderStrong,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            TextField(
                value = form.notes,
                onValueChange = { v -> vm.patchForm { it.copy(notes = v) } },
                placeholder = { Text("Add notes", color = NColors.textGhost, fontSize = 14.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = NColors.textSecondary, fontSize = 14.sp, fontFamily = NFonts.SpaceGrotesk),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(90.dp),
            )
            Spacer(Modifier.height(60.dp))
        }
    }

    // Nothing-styled date/time sheets — replaces the stock Android dialogs.
    when (picker) {
        PickerField.START_DATE -> NDatePickerSheet(
            title = "Start date", initial = form.startDate, weekStart = vm.weekStart,
            today = vm.today, accent = vm.accent,
            onDismiss = { picker = null },
            onConfirm = { d ->
                vm.patchForm { it.copy(startDate = d, endDate = if (it.endDate.isBefore(d)) d else it.endDate) }
                picker = null
            },
        )
        PickerField.END_DATE -> NDatePickerSheet(
            title = "End date", initial = form.endDate, weekStart = vm.weekStart,
            today = vm.today, accent = vm.accent,
            onDismiss = { picker = null },
            onConfirm = { d -> vm.patchForm { it.copy(endDate = d) }; picker = null },
        )
        PickerField.START_TIME -> NTimePickerSheet(
            title = "Start time", initial = form.startTime, ndot = vm.ndot, accent = vm.accent,
            onDismiss = { picker = null },
            onConfirm = { t ->
                vm.patchForm { it.copy(startTime = t, endTime = if (!it.endTime.isAfter(t)) t.plusHours(1) else it.endTime) }
                picker = null
            },
        )
        PickerField.END_TIME -> NTimePickerSheet(
            title = "End time", initial = form.endTime, ndot = vm.ndot, accent = vm.accent,
            onDismiss = { picker = null },
            onConfirm = { t -> vm.patchForm { it.copy(endTime = t) }; picker = null },
        )
        null -> {}
    }
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    showTime: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.border))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NColors.textSecondary, fontSize = 15.sp)
        Row {
            Text(
                CalendarFormats.fmtDateShort(date),
                color = NColors.textPrimary, fontFamily = NFonts.Mono, fontSize = 15.sp,
                modifier = Modifier
                    .background(NColors.surfaceHi, RoundedCornerShape(9.dp))
                    .clickable(onClick = onDateClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (showTime) {
                Spacer(Modifier.width(8.dp))
                Text(
                    CalendarFormats.fmtTime(time),
                    color = NColors.textPrimary, fontFamily = NFonts.Mono, fontSize = 15.sp,
                    modifier = Modifier
                        .background(NColors.surfaceHi, RoundedCornerShape(9.dp))
                        .clickable(onClick = onTimeClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Minimal flow-row: wraps chips onto new lines, mirrors the design's flex-wrap chip groups. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
    ) {
        content()
    }
}
