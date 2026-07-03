package com.ncalendar.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.ncalendar.app.R
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.Prefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Draws the home-screen widgets to bitmaps so we can use Nothing's Ndot / NType
 * fonts (RemoteViews can't set custom typefaces directly). Dark, minimal, spaced.
 */
object WidgetRenderer {

    private const val BG = 0xFF0A0A09.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DIM = 0xFF8A8A88.toInt()
    private const val FAINT = 0xFF565654.toInt()
    private const val GRID = 0xFF2A2A28.toInt()

    private fun ndot(context: Context) = ResourcesCompat.getFont(context, R.font.ndot55_regular) ?: Typeface.MONOSPACE
    private fun ndotCaps(context: Context) = ResourcesCompat.getFont(context, R.font.ndot55caps_regular) ?: Typeface.MONOSPACE
    private fun body(context: Context) = ResourcesCompat.getFont(context, R.font.ntype82_regular) ?: Typeface.DEFAULT
    private fun mono(context: Context) = ResourcesCompat.getFont(context, R.font.ntype82_mono) ?: Typeface.MONOSPACE

    private fun accent(context: Context) = Prefs(context).accentColorArgb

    private fun bitmap(w: Int, h: Int): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply { color = BG; isAntiAlias = true }
        val r = h * 0.14f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, bg)
        // Hairline border, same card language as the app's surfaces.
        val border = Paint().apply {
            color = GRID; isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, border)
        return bmp to canvas
    }

    /** Draws the app's "red dot + mono label" motif; returns for chaining consistency. */
    private fun labelWithDot(c: Canvas, context: Context, text: String, x: Float, baselineY: Float, p: Paint) {
        val r = p.textSize * 0.24f
        val dot = Paint().apply { color = accent(context); isAntiAlias = true }
        c.drawCircle(x + r, baselineY - p.textSize * 0.32f, r, dot)
        c.drawText(text, x + r * 2f + p.textSize * 0.5f, baselineY, p)
    }

    private fun paint(tf: Typeface, size: Float, color: Int, spacing: Float = 0f, align: Paint.Align = Paint.Align.LEFT) =
        Paint().apply {
            isAntiAlias = true
            typeface = tf
            textSize = size
            this.color = color
            letterSpacing = spacing
            textAlign = align
        }

    fun renderDotDate(context: Context, w: Int, h: Int): Bitmap {
        val (bmp, c) = bitmap(w, h)
        val today = LocalDate.now()
        val pad = w * 0.10f
        val weekday = CalendarFormats.DOW[CalendarFormats.dowIndex(today)]
        val month = CalendarFormats.MON_FULL[today.monthValue - 1]
        labelWithDot(c, context, weekday, pad, h * 0.30f, paint(mono(context), h * 0.11f, DIM, 0.18f))
        c.drawText(today.dayOfMonth.toString(), pad, h * 0.74f, paint(ndot(context), h * 0.46f, WHITE))
        c.drawText("$month ${today.year}", pad, h * 0.90f, paint(mono(context), h * 0.09f, FAINT, 0.14f))
        return bmp
    }

    fun renderNextEvent(context: Context, w: Int, h: Int, event: EventItem?, now: LocalDateTime): Bitmap {
        val (bmp, c) = bitmap(w, h)
        val pad = w * 0.08f
        labelWithDot(c, context, "NEXT EVENT", pad, h * 0.24f, paint(mono(context), h * 0.10f, FAINT, 0.18f))
        if (event == null) {
            c.drawText("Nothing scheduled", pad, h * 0.58f, paint(body(context), h * 0.16f, DIM))
            return bmp
        }
        // colored spine
        val spine = Paint().apply { color = event.color.toArgb(); isAntiAlias = true }
        c.drawRoundRect(pad, h * 0.34f, pad + w * 0.012f, h * 0.78f, 6f, 6f, spine)
        val textX = pad + w * 0.05f
        c.drawText(ellipsize(event.title, (w - textX - pad), paint(body(context), h * 0.17f, WHITE)), textX, h * 0.52f, paint(body(context), h * 0.17f, WHITE))
        c.drawText(CalendarFormats.timeLabelFor(event), textX, h * 0.72f, paint(ndot(context), h * 0.12f, DIM))
        val mins = ChronoUnit.MINUTES.between(now, event.start)
        if (mins in 0..(60 * 48)) {
            val cd = if (mins < 60) "in ${mins}m" else "in ${mins / 60}h"
            c.drawText(cd, w - pad, h * 0.24f, paint(ndot(context), h * 0.12f, accent(context), align = Paint.Align.RIGHT))
        }
        return bmp
    }

    fun renderAgenda(context: Context, w: Int, h: Int, events: List<EventItem>): Bitmap {
        val (bmp, c) = bitmap(w, h)
        val pad = w * 0.07f
        labelWithDot(c, context, "TODAY", pad, h * 0.16f, paint(mono(context), h * 0.075f, FAINT, 0.18f))
        if (events.isEmpty()) {
            c.drawText("All clear", pad, h * 0.5f, paint(body(context), h * 0.11f, DIM))
            return bmp
        }
        val rowH = h * 0.19f
        var y = h * 0.34f
        events.take(4).forEach { e ->
            val timePaint = paint(ndot(context), h * 0.085f, DIM)
            c.drawText(if (e.allDay) "ALL" else CalendarFormats.fmtTime(e.start.toLocalTime()), pad, y, timePaint)
            val barX = pad + w * 0.20f
            val bar = Paint().apply { color = e.color.toArgb(); isAntiAlias = true }
            c.drawRoundRect(barX, y - h * 0.10f, barX + w * 0.012f, y + h * 0.02f, 4f, 4f, bar)
            val titleX = barX + w * 0.05f
            val tp = paint(body(context), h * 0.10f, WHITE)
            c.drawText(ellipsize(e.title, w - titleX - pad, tp), titleX, y, tp)
            y += rowH
        }
        return bmp
    }

    fun renderMiniMonth(context: Context, w: Int, h: Int, eventDays: Set<LocalDate>): Bitmap {
        val (bmp, c) = bitmap(w, h)
        val today = LocalDate.now()
        val pad = w * 0.06f
        c.drawText(CalendarFormats.MON_FULL[today.monthValue - 1], pad, h * 0.13f, paint(body(context), h * 0.11f, WHITE))
        val first = today.withDayOfMonth(1)
        val offset = CalendarFormats.dowIndex(first) // week starts Sunday
        val gridStart = first.minusDays(offset.toLong())
        val cols = 7
        val rows = 6
        val cellW = (w - pad * 2) / cols
        val gridTop = h * 0.22f
        val cellH = (h - gridTop - pad) / rows
        val numPaint = paint(body(context), (cellH * 0.42f).coerceAtMost(cellW * 0.5f), WHITE, align = Paint.Align.CENTER)
        for (i in 0 until cols * rows) {
            val date = gridStart.plusDays(i.toLong())
            val col = i % cols
            val row = i / cols
            val cx = pad + cellW * col + cellW / 2f
            val cy = gridTop + cellH * row + cellH * 0.55f
            val inMonth = date.monthValue == today.monthValue
            val isToday = date == today
            if (isToday) {
                val ring = Paint().apply {
                    color = accent(context); isAntiAlias = true; style = Paint.Style.FILL
                }
                c.drawCircle(cx, cy - cellH * 0.16f, cellH * 0.36f, ring)
            }
            numPaint.color = when {
                isToday -> Color.BLACK
                inMonth -> WHITE
                else -> 0xFF3A3A38.toInt()
            }
            c.drawText(date.dayOfMonth.toString(), cx, cy, numPaint)
            if (inMonth && !isToday && date in eventDays) {
                val dot = Paint().apply { color = DIM; isAntiAlias = true }
                c.drawCircle(cx, cy + cellH * 0.22f, cellH * 0.06f, dot)
            }
        }
        return bmp
    }

    private fun ellipsize(text: String, maxWidth: Float, p: Paint): String {
        if (p.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && p.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        val a = (alpha * 255f).toInt() and 0xFF
        val r = (red * 255f).toInt() and 0xFF
        val g = (green * 255f).toInt() and 0xFF
        val b = (blue * 255f).toInt() and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
