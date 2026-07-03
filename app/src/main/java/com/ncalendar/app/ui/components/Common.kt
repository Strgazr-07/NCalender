package com.ncalendar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts
import com.ncalendar.app.ui.theme.NType

@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NColors.textFaint,
    size: TextUnit = NType.Label,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = NFonts.Mono,
        fontSize = size,
        letterSpacing = 2.sp,
    )
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).background(color, CircleShape))
}

@Composable
fun RoundIconButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(NColors.surfaceAlt, CircleShape)
            .border(1.dp, NColors.borderStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = NColors.textPrimary, fontSize = 22.sp)
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    leadingDot: Color? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val border = if (selected) (accentColor ?: NColors.textPrimary) else NColors.borderStrong
    val bg = if (selected) (accentColor?.copy(alpha = 0.14f) ?: NColors.surfaceSel2) else NColors.surface
    val textColor = if (selected) NColors.textPrimary else NColors.textMuted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        if (leadingDot != null) {
            Dot(leadingDot, size = 9.dp)
            Spacer(Modifier.size(9.dp))
        }
        Text(text = text, color = textColor, fontSize = 14.sp)
    }
}
