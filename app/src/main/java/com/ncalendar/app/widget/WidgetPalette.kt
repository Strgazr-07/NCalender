package com.ncalendar.app.widget

import android.content.Context
import android.content.res.Configuration
import com.ncalendar.app.data.Prefs
import com.ncalendar.app.data.ThemeMode

/**
 * Resolved colors for a widget render pass. Widgets are drawn to bitmaps on a background
 * thread with no Compose theme in scope, so they can't read NColors — this mirrors the app's
 * light/dark schemes (see ui/theme/Theme.kt) closely enough that a widget sitting next to the
 * app reads as the same product.
 */
data class WidgetPalette(
    val bg: Int,
    val border: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textDim: Int,
    val accent: Int,
    /** Contrast color for text/glyphs drawn ON TOP of [accent] (e.g. the "today" disc) —
     *  derived from the accent's luminance rather than hardcoded, because the accent is
     *  user-selectable and includes a near-white option that black-on-accent handles but
     *  white-on-accent renders invisible. */
    val onAccent: Int,
    val isDark: Boolean,
) {
    companion object {
        private const val DARK_BG = 0xFF0A0A09.toInt()
        private const val DARK_BORDER = 0xFF2A2A28.toInt()
        private const val DARK_TEXT = 0xFFFFFFFF.toInt()
        private const val DARK_TEXT_SECONDARY = 0xFFC8C8C6.toInt()
        private const val DARK_TEXT_DIM = 0xFF8A8A88.toInt()

        // Mirrors LightScheme in ui/theme/Theme.kt.
        private const val LIGHT_BG = 0xFFF6F5F1.toInt()
        private const val LIGHT_BORDER = 0xFFCBCAC1.toInt()
        private const val LIGHT_TEXT = 0xFF15150F.toInt()
        private const val LIGHT_TEXT_SECONDARY = 0xFF3A3A34.toInt()
        private const val LIGHT_TEXT_DIM = 0xFF6E6E68.toInt()

        fun resolve(context: Context): WidgetPalette {
            val prefs = Prefs(context)
            val dark = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                // The widget host's own configuration is the system theme as far as a widget
                // is concerned — it's what the launcher was inflated with.
                ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val accent = prefs.accentColorArgb
            return if (dark) {
                WidgetPalette(
                    bg = DARK_BG, border = DARK_BORDER, textPrimary = DARK_TEXT,
                    textSecondary = DARK_TEXT_SECONDARY, textDim = DARK_TEXT_DIM,
                    accent = accent, onAccent = onAccentFor(accent), isDark = true,
                )
            } else {
                WidgetPalette(
                    bg = LIGHT_BG, border = LIGHT_BORDER, textPrimary = LIGHT_TEXT,
                    textSecondary = LIGHT_TEXT_SECONDARY, textDim = LIGHT_TEXT_DIM,
                    accent = accent, onAccent = onAccentFor(accent), isDark = false,
                )
            }
        }

        /** Relative luminance (sRGB coefficients), thresholded — light accents get dark text
         *  on top, dark accents get light text. */
        fun onAccentFor(accentArgb: Int): Int {
            val r = ((accentArgb shr 16) and 0xFF) / 255f
            val g = ((accentArgb shr 8) and 0xFF) / 255f
            val b = (accentArgb and 0xFF) / 255f
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            return if (luminance > 0.6f) 0xFF15150F.toInt() else 0xFFFFFFFF.toInt()
        }
    }
}
