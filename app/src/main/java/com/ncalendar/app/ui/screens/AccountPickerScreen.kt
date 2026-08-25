package com.ncalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.accountKey
import com.ncalendar.app.data.ics.SubscriptionCalendars
import com.ncalendar.app.ui.components.MonoLabel
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.viewmodel.CalendarViewModel

/**
 * Shown once, the first time calendar access syncs in more than a couple of calendars — a
 * denylist ("everything visible unless hidden") means the very first sync would otherwise just
 * dump every calendar from every connected account straight into the app with no chance to
 * review first. This is a review, not an opt-in: everything starts checked, so a user who does
 * nothing gets exactly today's default-all-on behavior.
 */
@Composable
fun AccountPickerScreen(vm: CalendarViewModel) {
    val events by vm.events.collectAsState()
    val calendars by vm.calendars.collectAsState()
    val accounts = calendars
        .filterNot { it.accountName == SubscriptionCalendars.ACCOUNT_NAME }
        .groupBy { it.accountKey }.toList()

    Column(Modifier.fillMaxSize().background(NColors.bg).statusBarsPadding()) {
        Column(Modifier.padding(top = 24.dp, start = ScreenPad, end = ScreenPad, bottom = 8.dp)) {
            Text(
                "Review your calendars",
                color = NColors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = NFonts.NType82Headline,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "These calendars will show in NCalendar. Uncheck any account or calendar you'd rather keep out — you can always change this later in Settings.",
                color = NColors.textFaint, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPad),
        ) {
            AccountCalendarGroups(vm, accounts, events)
            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.padding(horizontal = ScreenPad).navigationBarsPadding().padding(bottom = 20.dp, top = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(vm.accent, RoundedCornerShape(16.dp))
                    .clickable { vm.finishAccountPicker() },
                contentAlignment = Alignment.Center,
            ) {
                MonoLabel("Done", color = Color.White)
            }
        }
    }
}
