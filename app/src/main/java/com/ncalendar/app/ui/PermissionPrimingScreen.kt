package com.ncalendar.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncalendar.app.ui.theme.NColors
import com.ncalendar.app.ui.theme.NFonts

/**
 * First-run priming screen (Nothing aesthetic: monochrome + accent) shown before the
 * system permission dialog, which the OS controls and we can't restyle.
 */
@Composable
fun PermissionPrimingScreen(
    accent: Color,
    onGrant: () -> Unit,
    onUseLocal: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NColors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 30.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        CalendarMark(accent)
        Spacer(Modifier.height(28.dp))
        Text(
            "NCALENDAR",
            color = accent,
            fontFamily = NFonts.Mono,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Connect your\ncalendar",
            color = NColors.textPrimary,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = NFonts.NType82Headline,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "NCalendar works with the calendars already on your phone — nothing leaves your device.",
            color = NColors.textDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(40.dp))
        FeatureRow(accent, "Your real events", "Read and edit Google, Outlook and local calendars.")
        Spacer(Modifier.height(22.dp))
        FeatureRow(accent, "Timely reminders", "Get a heads-up before events start, with snooze.")

        Spacer(Modifier.height(36.dp))
        Text(
            "HOW TO ENABLE",
            color = NColors.textFaint,
            fontFamily = NFonts.Mono,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(14.dp))
        StepRow(accent, "1", "Tap “Grant access” below.")
        Spacer(Modifier.height(10.dp))
        StepRow(accent, "2", "Choose “Allow” when Android asks for calendar access.")
        Spacer(Modifier.height(10.dp))
        StepRow(accent, "3", "No dialog? It opens Settings — tap Permissions → Calendar → Allow.")

        Spacer(Modifier.weight(1f))

        // Primary — accent (red). Text contrast is against the accent, not the theme.
        val onAccent = if (accent.luminance() > 0.6f) Color(0xFF15150F) else Color(0xFFF6F5F1)
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(accent, RoundedCornerShape(16.dp))
                .clickable(onClick = onGrant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "GRANT ACCESS",
                color = onAccent,
                fontFamily = NFonts.Mono,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        // Privacy opt-out — no accounts, everything stays in the on-device store.
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, NColors.borderStrong, RoundedCornerShape(16.dp))
                .clickable(onClick = onUseLocal)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("USE OFFLINE", color = NColors.textSecondary, fontFamily = NFonts.Mono, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(3.dp))
            Text("No accounts — events stay on this phone", color = NColors.textFaint, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(accent: Color, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).background(accent.copy(alpha = 0.14f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(9.dp).background(accent, CircleShape))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = NColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = NColors.textFaint, fontSize = 13.5.sp, lineHeight = 19.sp)
        }
    }
}

/** The app's calendar mark — same geometry as the launcher icon: outlined body with
 *  binder tabs, header divider, and a dot-matrix day grid with a red "today" dot. */
@Composable
private fun CalendarMark(accent: Color) {
    val outline = NColors.textPrimary
    val dayDot = NColors.textDim
    Canvas(Modifier.size(58.dp, 64.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = w * 0.085f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        // Binder tabs
        listOf(0.29f, 0.71f).forEach { xF ->
            drawLine(
                outline,
                Offset(w * xF, 0f),
                Offset(w * xF, h * 0.24f),
                strokeWidth = stroke.width,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        // Calendar body
        val bodyTop = h * 0.13f
        drawRoundRect(
            outline,
            topLeft = Offset(stroke.width / 2f, bodyTop),
            size = androidx.compose.ui.geometry.Size(w - stroke.width, h - bodyTop - stroke.width / 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.17f, w * 0.17f),
            style = stroke,
        )
        // Header divider
        val divY = h * 0.36f
        drawLine(outline, Offset(stroke.width / 2f, divY), Offset(w - stroke.width / 2f, divY), strokeWidth = stroke.width * 0.9f)
        // Day dots (middle-top is today, in the accent red)
        val r = w * 0.075f
        val xs = listOf(0.21f, 0.5f, 0.79f)
        val ys = listOf(0.58f, 0.81f)
        ys.forEachIndexed { row, yF ->
            xs.forEachIndexed { col, xF ->
                val isToday = row == 0 && col == 1
                drawCircle(if (isToday) accent else dayDot, radius = r, center = Offset(w * xF, h * yF))
            }
        }
    }
}

@Composable
private fun StepRow(accent: Color, num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            num,
            color = accent,
            fontFamily = NFonts.Mono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        Text(text, color = NColors.textDim, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
