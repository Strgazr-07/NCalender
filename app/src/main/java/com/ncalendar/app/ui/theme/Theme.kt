package com.ncalendar.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** The Nothing red — the app's single fixed accent. */
val AccentRed = Color(0xFFD71921)

/** All the semantic colour tokens the UI reads. Swapped wholesale for light/dark. */
data class NScheme(
    val bg: Color,
    val bgElevated: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceHi: Color,
    val surfaceSel: Color,
    val surfaceSel2: Color,
    val surfaceInset: Color,
    val border: Color,
    val borderStrong: Color,
    val borderSubtle: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDim: Color,
    val textFaint: Color,
    val textFainter: Color,
    val textGhost: Color,
    val textGhostDeep: Color,
    val textDisabledDot: Color,
    val dimNum: Color,
    // High-contrast "inverse" surface for FAB / primary buttons / selected tabs.
    val inverseBg: Color,
    val onInverse: Color,
    val accent: Color = AccentRed,
    val defaultAccent: Color = AccentRed,
    val isDark: Boolean,
)

private val DarkScheme = NScheme(
    bg = Color(0xFF000000),
    bgElevated = Color(0xFF0A0A09),
    surface = Color(0xFF0C0C0C),
    surfaceAlt = Color(0xFF0F0F0F),
    surfaceHi = Color(0xFF111111),
    surfaceSel = Color(0xFF141412),
    surfaceSel2 = Color(0xFF1A1A18),
    surfaceInset = Color(0xFF2A2A28),
    border = Color(0xFF1C1C1A),
    borderStrong = Color(0xFF232320),
    borderSubtle = Color(0xFF1E1E1C),
    divider = Color(0xFF141412),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFE6E6E4),
    textMuted = Color(0xFFA8A8A6),
    textDim = Color(0xFF8A8A88),
    textFaint = Color(0xFF6A6A68),
    textFainter = Color(0xFF565654),
    textGhost = Color(0xFF4A4A48),
    textGhostDeep = Color(0xFF3A3A38),
    textDisabledDot = Color(0xFF3F3F3D),
    dimNum = Color(0xFFC8C8C6),
    inverseBg = Color(0xFFFFFFFF),
    onInverse = Color(0xFF000000),
    isDark = true,
)

private val LightScheme = NScheme(
    bg = Color(0xFFF6F5F1),
    bgElevated = Color(0xFFEFEEE9),
    surface = Color(0xFFECEBE6),
    surfaceAlt = Color(0xFFE6E5DF),
    surfaceHi = Color(0xFFE0DFD8),
    surfaceSel = Color(0xFFE2E1DA),
    surfaceSel2 = Color(0xFFDAD9D2),
    surfaceInset = Color(0xFFD2D1C9),
    border = Color(0xFFDAD9D2),
    borderStrong = Color(0xFFCBCAC1),
    borderSubtle = Color(0xFFD8D7D0),
    divider = Color(0xFFE2E1DA),
    textPrimary = Color(0xFF15150F),
    textSecondary = Color(0xFF2A2A26),
    textMuted = Color(0xFF5A5A55),
    textDim = Color(0xFF6E6E68),
    textFaint = Color(0xFF8A8A82),
    textFainter = Color(0xFF9A9A92),
    textGhost = Color(0xFFAEAEA6),
    textGhostDeep = Color(0xFFC2C2BA),
    textDisabledDot = Color(0xFFBEBEB6),
    dimNum = Color(0xFF3A3A34),
    inverseBg = Color(0xFF15150F),
    onInverse = Color(0xFFF6F5F1),
    isDark = false,
)

val LocalNScheme = staticCompositionLocalOf { DarkScheme }

/** Drop-in replacement for the old static object — now theme-aware. */
val NColors: NScheme
    @Composable @ReadOnlyComposable
    get() = LocalNScheme.current

@Composable
fun NCalendarTheme(
    dark: Boolean = true,
    accent: Color = AccentRed,
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) DarkScheme else LightScheme
    val colorScheme = if (dark) {
        darkColorScheme(background = scheme.bg, surface = scheme.surface, primary = accent, onBackground = scheme.textPrimary, onSurface = scheme.textPrimary)
    } else {
        lightColorScheme(background = scheme.bg, surface = scheme.surface, primary = accent, onBackground = scheme.textPrimary, onSurface = scheme.textPrimary)
    }
    val defaultTextStyle = TextStyle(fontFamily = NFonts.Body, color = scheme.textPrimary)
    CompositionLocalProvider(LocalNScheme provides scheme) {
        MaterialTheme(colorScheme = colorScheme) {
            CompositionLocalProvider(LocalTextStyle provides defaultTextStyle, content = content)
        }
    }
}
