package com.ncalendar.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import com.ncalendar.app.ui.screens.AppScreen
import com.ncalendar.app.ui.screens.CalendarsScreen
import com.ncalendar.app.ui.screens.DetailScreen
import com.ncalendar.app.ui.screens.DayEventsScreen
import com.ncalendar.app.ui.screens.EditorScreen
import com.ncalendar.app.ui.screens.SearchScreen
import com.ncalendar.app.ui.screens.SettingsScreen
import com.ncalendar.app.viewmodel.CalendarViewModel
import com.ncalendar.app.viewmodel.Screen

@Composable
fun AppRoot(vm: CalendarViewModel) {
    BackHandler(enabled = vm.canGoBack) { vm.onBack() }

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
        }
    }
}
