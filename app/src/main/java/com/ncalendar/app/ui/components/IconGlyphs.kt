package com.ncalendar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ncalendar.app.ui.theme.NColors

/** Small hand-drawn glyphs matching the design mockup's simple line-icon language. */
@Composable
fun SearchGlyph(color: Color = NColors.textDim, size: Dp = 17.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension * 0.32f
        val cx = this.size.width * 0.42f
        val cy = this.size.height * 0.42f
        drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.8f * density))
        val start = Offset(cx + r * 0.72f, cy + r * 0.72f)
        val end = Offset(this.size.width * 0.92f, this.size.height * 0.92f)
        drawLine(color, start, end, strokeWidth = 1.8f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
fun ListGlyph(color: Color, size: Dp = 17.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val rowYs = listOf(0.25f, 0.5f, 0.75f)
        rowYs.forEach { f ->
            val y = this.size.height * f
            drawLine(color, Offset(this.size.width * 0.32f, y), Offset(this.size.width * 0.95f, y), strokeWidth = 1.8f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(color, radius = 1.5f * density, center = Offset(this.size.width * 0.08f, y))
        }
    }
}

/** Classic cog: body ring + hub + eight stubby teeth, drawn in the thin-line language. */
@Composable
fun GearGlyph(color: Color = NColors.textDim, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val strokeW = 1.8f * density
        val body = this.size.minDimension * 0.30f
        drawCircle(color, radius = body, center = c, style = Stroke(width = strokeW))
        drawCircle(color, radius = this.size.minDimension * 0.12f, center = c, style = Stroke(width = strokeW))
        for (i in 0 until 8) {
            val a = (i * Math.PI / 4 + Math.PI / 8).toFloat()
            val inner = body + strokeW * 0.4f
            val outer = this.size.minDimension * 0.47f
            val p1 = Offset(c.x + inner * kotlin.math.cos(a), c.y + inner * kotlin.math.sin(a))
            val p2 = Offset(c.x + outer * kotlin.math.cos(a), c.y + outer * kotlin.math.sin(a))
            drawLine(color, p1, p2, strokeWidth = 2.6f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@Composable
fun CalendarGlyph(color: Color = NColors.textDim, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokeW = 1.8f * density
        drawRoundRect(
            color,
            topLeft = Offset(this.size.width * 0.14f, this.size.height * 0.2f),
            size = androidx.compose.ui.geometry.Size(this.size.width * 0.72f, this.size.height * 0.68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * density, 3f * density),
            style = Stroke(width = strokeW),
        )
        drawLine(color, Offset(this.size.width * 0.14f, this.size.height * 0.38f), Offset(this.size.width * 0.86f, this.size.height * 0.38f), strokeWidth = strokeW)
    }
}

@Composable
fun TrashGlyph(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokeW = 1.6f * density
        val w = this.size.width
        val h = this.size.height
        drawLine(color, Offset(w * 0.2f, h * 0.28f), Offset(w * 0.8f, h * 0.28f), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.28f), Offset(w * 0.36f, h * 0.16f), strokeWidth = strokeW)
        drawLine(color, Offset(w * 0.64f, h * 0.28f), Offset(w * 0.64f, h * 0.16f), strokeWidth = strokeW)
        drawLine(color, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.32f, h * 0.86f), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.68f, h * 0.86f), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.86f), Offset(w * 0.68f, h * 0.86f), strokeWidth = strokeW)
    }
}

@Composable
fun LocationGlyph(color: Color = NColors.textFaint, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokeW = 1.6f * density
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        drawCircle(color, radius = this.size.minDimension * 0.16f, center = Offset(cx, h * 0.38f), style = Stroke(width = strokeW))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - w * 0.28f, h * 0.4f)
            cubicTo(cx - w * 0.28f, h * 0.75f, cx, h * 0.95f, cx, h * 0.95f)
            cubicTo(cx, h * 0.95f, cx + w * 0.28f, h * 0.75f, cx + w * 0.28f, h * 0.4f)
        }
        drawPath(path, color, style = Stroke(width = strokeW))
    }
}

@Composable
fun BellGlyph(color: Color = NColors.textFaint, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokeW = 1.6f * density
        val w = this.size.width
        val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.65f)
            cubicTo(w * 0.25f, h * 0.35f, w * 0.32f, h * 0.15f, w * 0.5f, h * 0.15f)
            cubicTo(w * 0.68f, h * 0.15f, w * 0.75f, h * 0.35f, w * 0.75f, h * 0.65f)
            cubicTo(w * 0.75f, h * 0.72f, w * 0.85f, h * 0.75f, w * 0.85f, h * 0.75f)
            lineTo(w * 0.15f, h * 0.75f)
            cubicTo(w * 0.15f, h * 0.75f, w * 0.25f, h * 0.72f, w * 0.25f, h * 0.65f)
            close()
        }
        drawPath(path, color, style = Stroke(width = strokeW))
        drawLine(color, Offset(w * 0.42f, h * 0.85f), Offset(w * 0.58f, h * 0.85f), strokeWidth = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
fun RepeatGlyph(color: Color = NColors.textFaint, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokeW = 1.6f * density
        drawArc(
            color,
            startAngle = -140f,
            sweepAngle = 230f,
            useCenter = false,
            style = Stroke(width = strokeW),
        )
        drawArc(
            color,
            startAngle = 40f,
            sweepAngle = 230f,
            useCenter = false,
            style = Stroke(width = strokeW),
        )
    }
}

@Composable
fun PlusFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(NColors.inverseBg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text("+", color = NColors.onInverse, fontSize = 28.sp2(), fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
    }
}

private fun Int.sp2() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
