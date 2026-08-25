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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.RepeatRule
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.NCustomRecurrenceSheet
import com.ncalendar.app.ui.components.NDatePickerSheet
import com.ncalendar.app.ui.components.NReminderPickerSheet
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
    val events by vm.events.collectAsState()
    val isEditing = vm.state.editId != null
    var picker by remember { mutableStateOf<PickerField?>(null) }
    var remPickerOpen by remember { mutableStateOf(false) }
    var customRepeatOpen by remember { mutableStateOf(false) }
    // Dismiss the keyboard before opening a wheel sheet — otherwise the IME sits
    // over the picker on tall keyboards (e.g. Nothing Phone 3) and hides it.
    val focus = LocalFocusManager.current

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonoLabel(if (isEditing) "Edit event" else "New event", color = NColors.textSecondary, size = NType.LabelBig)
                // Surfaces which part of the series this save will touch — otherwise it's easy
                // to forget which choice was made on the scope sheet a moment ago.
                if (form.scope == com.ncalendar.app.data.EditScope.THIS_EVENT) {
                    MonoLabel("This event only", color = vm.accent, size = 10.sp)
                }
            }
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
                textStyle = androidx.compose.ui.text.TextStyle(color = NColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = NFonts.Body),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = NColors.borderStrong,
                    unfocusedIndicatorColor = NColors.borderStrong,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isEditing) {
                Spacer(Modifier.height(12.dp))
                FlowRow {
                    Pill(
                        text = "Smart fill",
                        selected = false,
                        accentColor = vm.accent,
                        onClick = {
                            val parsed = CalendarFormats.parseQuick(form.title, vm.today)
                            vm.patchForm {
                                it.copy(
                                    title = parsed.title ?: it.title,
                                    startDate = parsed.date,
                                    endDate = parsed.date,
                                    startTime = parsed.startTime ?: it.startTime,
                                    endTime = parsed.endTime ?: it.endTime,
                                )
                            }
                        },
                    )
                    Pill(
                        text = "Birthday",
                        selected = false,
                        accentColor = vm.accent,
                        onClick = {
                            vm.patchForm {
                                it.copy(
                                    title = it.title.ifBlank { "Birthday" },
                                    allDay = true,
                                    endDate = it.startDate,
                                    repeat = RepeatRule.YEARLY,
                                    repeatInterval = 1,
                                    repeatByDays = emptySet(),
                                    repeatEndDate = null,
                                    repeatEndCount = null,
                                    reminders = listOf(1440),
                                )
                            }
                        },
                    )
                    Pill(
                        text = "Meeting",
                        selected = false,
                        accentColor = vm.accent,
                        onClick = {
                            vm.patchForm {
                                it.copy(
                                    title = it.title.ifBlank { "Meeting" },
                                    allDay = false,
                                    reminders = listOf(vm.defaultReminder),
                                )
                            }
                        },
                    )
                }
            }

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
                onDateClick = { focus.clearFocus(); picker = PickerField.START_DATE },
                onTimeClick = { focus.clearFocus(); picker = PickerField.START_TIME },
            )
            DateTimeRow(
                label = "Ends",
                date = form.endDate,
                time = form.endTime,
                showTime = !form.allDay,
                onDateClick = { focus.clearFocus(); picker = PickerField.END_DATE },
                onTimeClick = { focus.clearFocus(); picker = PickerField.END_TIME },
            )

            // A single split-out occurrence can't carry its own repeat rule — saveEvent()
            // always forces it to NONE for a THIS_EVENT scope, so showing (and letting the
            // user fiddle with) the series' rule here would be actively misleading.
            if (form.scope != com.ncalendar.app.data.EditScope.THIS_EVENT) {
                MonoLabel("Repeat", color = NColors.textFaint, modifier = Modifier.padding(top = 20.dp, bottom = 9.dp))
                FlowRow {
                    RepeatRule.entries.forEach { r ->
                        Pill(
                            text = r.label,
                            selected = form.repeat == r && form.repeatInterval == 1 && form.repeatByDays.isEmpty() &&
                                form.repeatEndDate == null && form.repeatEndCount == null,
                            onClick = {
                                vm.patchForm {
                                    it.copy(
                                        repeat = r,
                                        repeatInterval = 1,
                                        repeatByDays = emptySet(),
                                        repeatEndDate = null,
                                        repeatEndCount = null,
                                    )
                                }
                            },
                        )
                    }
                    Pill(text = "Custom...", selected = false, accentColor = vm.accent, onClick = { focus.clearFocus(); customRepeatOpen = true })
                }
                if (form.repeat != RepeatRule.NONE) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        CalendarFormats.repeatSummary(form.repeat, form.repeatInterval, form.repeatByDays, form.repeatEndDate, form.repeatEndCount),
                        color = NColors.textMuted,
                        fontFamily = NFonts.Mono,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(NColors.surfaceAlt, RoundedCornerShape(10.dp))
                            .clickable { customRepeatOpen = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            MonoLabel("Reminders", color = NColors.textFaint, modifier = Modifier.padding(top = 20.dp, bottom = 9.dp))
            val reminderPresets = listOf(0 to "At time", 10 to "10 min", 30 to "30 min", 60 to "1 hr", 1440 to "1 day")
            val presetValues = reminderPresets.map { it.first }.toSet()
            FlowRow {
                reminderPresets.forEach { (m, label) ->
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
                // Any custom (non-preset) reminders show as their own removable chips.
                form.reminders.filterNot { it in presetValues }.sorted().forEach { m ->
                    Pill(
                        text = CalendarFormats.reminderLabel(m),
                        selected = true,
                        accentColor = vm.accent,
                        onClick = { vm.patchForm { f -> f.copy(reminders = f.reminders.filterNot { it == m }) } },
                    )
                }
                Pill(text = "Custom…", selected = false, onClick = { focus.clearFocus(); remPickerOpen = true })
            }

            TextField(
                value = form.location,
                onValueChange = { v -> vm.patchForm { it.copy(location = v) } },
                placeholder = { Text("Add location", color = NColors.textGhost, fontSize = 15.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = NColors.textPrimary, fontSize = 15.sp, fontFamily = NFonts.Body),
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
                textStyle = androidx.compose.ui.text.TextStyle(color = NColors.textSecondary, fontSize = 14.sp, fontFamily = NFonts.Body),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(90.dp),
            )
            val conflicts = vm.conflictsFor(events, form)
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val first = conflicts.first()
                val extra = if (conflicts.size > 1) " +${conflicts.size - 1} more" else ""
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(vm.accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(vm.accent, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Overlaps with \"${first.title}\" ${CalendarFormats.timeLabelFor(first)}$extra",
                        color = NColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
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
            onConfirm = { d ->
                vm.patchForm {
                    // Mirrors the start-date clamp above: an end date can't land before the
                    // start date, and if clamping lands both on the same day, push the end
                    // time forward so the event doesn't silently collapse to zero duration.
                    val endDate = if (d.isBefore(it.startDate)) it.startDate else d
                    val endTime = if (endDate == it.startDate && !it.endTime.isAfter(it.startTime)) {
                        it.startTime.plusMinutes(15)
                    } else it.endTime
                    it.copy(endDate = endDate, endTime = endTime)
                }
                picker = null
            },
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
            onConfirm = { t ->
                vm.patchForm {
                    // Same-day end time can't land at or before the start time.
                    val endTime = if (it.endDate == it.startDate && !t.isAfter(it.startTime)) {
                        it.startTime.plusMinutes(15)
                    } else t
                    it.copy(endTime = endTime)
                }
                picker = null
            },
        )
        null -> {}
    }

    if (remPickerOpen) {
        NReminderPickerSheet(
            accent = vm.accent,
            ndot = vm.ndot,
            onDismiss = { remPickerOpen = false },
            onConfirm = { minutes ->
                vm.patchForm { f -> f.copy(reminders = (f.reminders + minutes).distinct()) }
                remPickerOpen = false
            },
        )
    }
    if (customRepeatOpen) {
        NCustomRecurrenceSheet(
            initialRule = form.repeat,
            initialInterval = form.repeatInterval,
            initialByDays = form.repeatByDays,
            initialUntil = form.repeatEndDate,
            initialCount = form.repeatEndCount,
            weekStart = vm.weekStart,
            today = vm.today,
            accent = vm.accent,
            ndot = vm.ndot,
            onDismiss = { customRepeatOpen = false },
            onConfirm = { rule, interval, byDays, until, count ->
                vm.patchForm {
                    it.copy(
                        repeat = rule,
                        repeatInterval = interval,
                        repeatByDays = byDays,
                        repeatEndDate = until,
                        repeatEndCount = count,
                    )
                }
                customRepeatOpen = false
            },
        )
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
