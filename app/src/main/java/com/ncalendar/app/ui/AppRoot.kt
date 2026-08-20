package com.ncalendar.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.data.EditScope
import com.ncalendar.app.viewmodel.PendingScopeAction
import com.ncalendar.app.ui.components.NRecurringScopeSheet
import com.ncalendar.app.ui.screens.AccountPickerScreen
import com.ncalendar.app.ui.screens.AppScreen
import com.ncalendar.app.ui.screens.CalendarsScreen
import com.ncalendar.app.ui.screens.DetailScreen
import com.ncalendar.app.ui.screens.DayEventsScreen
import com.ncalendar.app.ui.screens.EditorScreen
import com.ncalendar.app.ui.screens.SearchScreen
import com.ncalendar.app.ui.screens.SettingsScreen
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.viewmodel.CalendarViewModel
import com.ncalendar.app.viewmodel.Screen

@Composable
fun AppRoot(vm: CalendarViewModel) {
    BackHandler(enabled = vm.canGoBack) { vm.onBack() }
    val events by vm.events.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // Sub-screens settle in over the calendar with a quick fade + scale; popping
        // back reverses the depth so the calendar reads as "underneath".
        AnimatedContent(
            targetState = vm.state.screen,
            transitionSpec = {
                if (targetState == Screen.APP) {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 1.03f, animationSpec = tween(160)))
                } else {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 1.04f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.98f, animationSpec = tween(160)))
                }
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.APP -> AppScreen(vm)
                Screen.DETAIL -> DetailScreen(vm)
                Screen.EDITOR -> EditorScreen(vm)
                Screen.ACCOUNTS -> CalendarsScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.SEARCH -> SearchScreen(vm)
                Screen.DAY_EVENTS -> DayEventsScreen(vm)
                Screen.ACCOUNT_PICKER -> AccountPickerScreen(vm)
            }
        }

        // Floats above whichever screen triggered it (DetailScreen, LongPressMenu, or a
        // drag in DayView) — see CalendarViewModel.PendingScopeAction.
        val pending = vm.state.pendingScopeAction
        if (pending != null) {
            NRecurringScopeSheet(
                accent = vm.accent,
                isDelete = pending is PendingScopeAction.Delete,
                onDismiss = { vm.cancelScope() },
                onThisEvent = { vm.confirmScope(events, EditScope.THIS_EVENT) },
                onAllEvents = { vm.confirmScope(events, EditScope.ALL_EVENTS) },
            )
        }

        ErrorBanner(vm)
    }
}

@Composable
private fun BoxScope.ErrorBanner(vm: CalendarViewModel) {
    val message = vm.state.errorMessage
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 3 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 90.dp),
    ) {
        Box(
            Modifier
                .background(NColors.surfaceHi, RoundedCornerShape(14.dp))
                .border(1.dp, NColors.borderStrong, RoundedCornerShape(14.dp))
                .clickable { vm.clearError() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(message ?: "", color = NColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
