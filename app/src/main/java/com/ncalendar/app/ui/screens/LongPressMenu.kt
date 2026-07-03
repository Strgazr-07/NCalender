package com.ncalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel

@Composable
fun LongPressMenu(vm: CalendarViewModel, events: List<EventItem>) {
    val id = vm.state.menuEventId ?: return
    val e = events.find { it.id == id } ?: return

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { vm.closeMenu() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(230.dp)
                .background(NColors.surface, RoundedCornerShape(16.dp)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(e.title, color = NColors.textPrimary, fontSize = 14.sp, maxLines = 1)
                Text(CalendarFormats.timeLabelFor(e), color = NColors.textDim, fontFamily = NFonts.Mono, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.border))
            MenuAction("Open", NColors.textPrimary) { vm.closeMenu(); vm.openEvent(e.id) }
            MenuAction("Edit", NColors.textPrimary) { vm.closeMenu(); vm.openEdit(events) }
            MenuAction("Duplicate", NColors.textPrimary) { vm.dupById(events, e.id) }
            MenuAction("Delete", vm.accent) { vm.deleteById(e.id) }
        }
    }
}

@Composable
private fun MenuAction(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NColors.divider))
    Text(
        label,
        color = color,
        fontSize = 14.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}
