package com.ncalendar.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.ncalendar.app.R

/**
 * App-wide type scale. Use these instead of ad-hoc sp values so every screen
 * shares the same hierarchy: H1 page titles, H2 event/detail titles, H3 row
 * titles, Body for regular text, Label for uppercase mono captions/actions.
 */
object NType {
    val H1 = 34.sp
    val H2 = 28.sp
    val H3 = 17.sp
    val Body = 15.sp
    val Label = 12.sp
    val LabelBig = 14.sp // prominent mono actions (Save / Cancel / bar titles)
}

/**
 * Nothing Tech's own typefaces (Ndot dot-matrix, NType headline/body), bundled from
 * https://github.com/xeji01/nothingfont for this personal prototype. These are
 * Nothing's proprietary fonts — fine for a device-local build, but do not redistribute
 * this app publicly without Nothing's permission to use them.
 */
object NFonts {
    val Ndot55 = FontFamily(Font(R.font.ndot55_regular))
    val Ndot57 = FontFamily(Font(R.font.ndot57_regular))
    val Ndot57Caps = FontFamily(Font(R.font.ndot57caps_regular))
    val Ndot55Caps = FontFamily(Font(R.font.ndot55caps_regular))
    val NType82 = FontFamily(Font(R.font.ntype82_regular))
    val NType82Headline = FontFamily(Font(R.font.ntype82_headline))
    val NType82Mono = FontFamily(Font(R.font.ntype82_mono))
    val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk_regular))
    val SpaceMono = FontFamily(Font(R.font.space_mono_regular))

    /** The app-wide default font (Nothing's UI typeface). Every plain Text() uses this. */
    val Body = NType82

    /** Uppercase mono labels (section headers, chips, hour ticks). */
    val Mono = NType82Mono

    /**
     * Large display titles (month name).
     * - Ndot on  → Ndot-55 caps (dot-matrix with breathing space between dots — the
     *   spaced Nothing look; Ndot-57 packs the dots too tightly).
     * - Ndot off → the same body typeface as event titles, so everything matches.
     */
    fun display(ndot: Boolean) = if (ndot) Ndot55Caps else NType82

    /** Numerals (clock, agenda dates, times). Dot-matrix (spaced Ndot-55) when Ndot is on. */
    fun numeral(ndot: Boolean) = if (ndot) Ndot55 else NType82
}
