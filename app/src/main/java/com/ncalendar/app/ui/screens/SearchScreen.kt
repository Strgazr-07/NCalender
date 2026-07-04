package com.ncalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.Calendars
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.components.RoundIconButton
import com.ncalendar.app.ui.components.SearchGlyph
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: CalendarViewModel) {
    val events by vm.events.collectAsState()
    val q = vm.state.searchQuery
    val results = vm.searchResults(events)

    Column(Modifier.fillMaxSize().background(NColors.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, start = ScreenPad, end = ScreenPad, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton("‹", onClick = { vm.backToApp() })
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(NColors.surfaceAlt, RoundedCornerShape(13.dp))
                    .border(1.dp, NColors.borderStrong, RoundedCornerShape(13.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchGlyph()
                Spacer(Modifier.width(10.dp))
                TextField(
                    value = q,
                    onValueChange = { vm.onSearchQuery(it) },
                    placeholder = { Text("Search events", color = NColors.textGhost, fontSize = 15.sp) },
                    textStyle = TextStyle(color = NColors.textPrimary, fontSize = 15.sp, fontFamily = NFonts.Body),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val calendars by vm.calendars.collectAsState()
        FilterBar(vm, calendars)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = ScreenPad),
        ) {
            if (q.isBlank() && !vm.searchFiltersActive) {
                if (vm.recentSearches.isNotEmpty()) {
                    MonoLabel("Recent", color = NColors.textFaint, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        vm.recentSearches.forEach { s ->
                            Text(
                                s, color = NColors.dimNum, fontSize = 13.sp,
                                modifier = Modifier
                                    .background(NColors.surface, RoundedCornerShape(20.dp))
                                    .border(1.dp, NColors.borderStrong, RoundedCornerShape(20.dp))
                                    .clickable { vm.onSearchQuery(s) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        "Search your events by title.",
                        color = NColors.textGhost, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
            } else if (results.isNotEmpty()) {
                MonoLabel("${results.size} result${if (results.size == 1) "" else "s"}", color = NColors.textFainter, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                results.forEach { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openSearchResult(e.id) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(38.dp).background(e.color, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(e.title, color = NColors.textPrimary, fontSize = 15.sp)
                            val label = "${CalendarFormats.DOW[CalendarFormats.dowIndex(e.startDate)]} ${CalendarFormats.MON[e.startDate.monthValue - 1]} ${e.startDate.dayOfMonth}" +
                                if (e.allDay) " · all-day" else " · ${CalendarFormats.fmtTime(e.start.toLocalTime())}"
                            Text(label, color = NColors.textDim, fontFamily = NFonts.Mono, fontSize = 11.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.divider))
                }
            } else {
                val msg = if (q.isBlank()) "No events match these filters." else "No events match “$q”."
                Text(msg, color = NColors.textGhost, fontSize = 14.sp, modifier = Modifier.padding(top = 40.dp))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(vm: CalendarViewModel, calendars: List<com.ncalendar.app.data.CalendarInfo>) {
    Column(Modifier.padding(horizontal = ScreenPad, vertical = 4.dp)) {
        // Date range segmented chips
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            com.ncalendar.app.viewmodel.SearchRange.entries.forEach { r ->
                val sel = vm.state.searchRange == r
                Text(
                    r.label,
                    color = if (sel) NColors.onInverse else NColors.textDim,
                    fontFamily = NFonts.Mono,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(if (sel) NColors.textPrimary else NColors.surface, RoundedCornerShape(18.dp))
                        .border(1.dp, if (sel) NColors.textPrimary else NColors.borderStrong, RoundedCornerShape(18.dp))
                        .clickable { vm.setSearchRange(r) }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
        if (calendars.size > 1) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                calendars.forEach { c ->
                    val sel = c.id in vm.state.searchCalendars
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(if (sel) c.color.copy(alpha = 0.16f) else NColors.surface, RoundedCornerShape(18.dp))
                            .border(1.dp, if (sel) c.color else NColors.borderStrong, RoundedCornerShape(18.dp))
                            .clickable { vm.toggleSearchCalendar(c.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(Modifier.size(9.dp).background(c.color, androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(c.name, color = if (sel) NColors.textPrimary else NColors.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
