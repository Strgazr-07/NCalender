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
    private const val LIGHT = 0xFFC8C8C6.toInt()
    private const val DIM = 0xFF8A8A88.toInt()
    private const val GRID = 0xFF2A2A28.toInt()

    // Loaded once and reused — ResourcesCompat.getFont() re-parses the font file
    // on every call otherwise, and every widget refresh renders 1-4 of these.
    private var ndotTf: Typeface? = null
    private var ndotCapsTf: Typeface? = null
    private var bodyTf: Typeface? = null
    private var monoTf: Typeface? = null

    private fun ndot(context: Context): Typeface =
        ndotTf ?: (ResourcesCompat.getFont(context, R.font.ndot55_regular) ?: Typeface.MONOSPACE).also { ndotTf = it }

    private fun ndotCaps(context: Context): Typeface =
        ndotCapsTf ?: (ResourcesCompat.getFont(context, R.font.ndot55caps_regular) ?: Typeface.MONOSPACE).also { ndotCapsTf = it }

    private fun body(context: Context): Typeface =
        bodyTf ?: (ResourcesCompat.getFont(context, R.font.ntype82_regular) ?: Typeface.DEFAULT).also { bodyTf = it }

    private fun mono(context: Context): Typeface =
        monoTf ?: (ResourcesCompat.getFont(context, R.font.ntype82_mono) ?: Typeface.MONOSPACE).also { monoTf = it }

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
        val pad = w * 0.11f
        val weekday = CalendarFormats.DOW[CalendarFormats.dowIndex(today)]
        val month = CalendarFormats.MON_FULL[today.monthValue - 1]

        // Weekday top-left, dot-matrix caps in white with the red-dot motif.
        labelWithDot(c, context, weekday, pad, h * 0.185f, paint(ndotCaps(context), h * 0.095f, WHITE, 0.1f))

        // The date, big and horizontally centered (NType82 — the app's default face).
        val numPaint = paint(body(context), h * 0.42f, WHITE, 0f, Paint.Align.CENTER)
        val centerY = h * 0.55f
        val baseline = centerY - (numPaint.ascent() + numPaint.descent()) / 2f
        c.drawText(today.dayOfMonth.toString(), w / 2f, baseline, numPaint)

        // Month + year centered at the bottom.
        c.drawText(
            "$month ${today.year}",
            w / 2f, h * 0.915f,
            paint(mono(context), h * 0.07f, WHITE, 0.14f, Paint.Align.CENTER),
        )
        return bmp
    }

    fun renderNextEvent(context: Context, w: Int, h: Int, event: EventItem?, now: LocalDateTime): Bitmap {
        val (bmp, c) = bitmap(w, h)
        val pad = w * 0.08f
        labelWithDot(c, context, "NEXT EVENT", pad, h * 0.24f, paint(mono(context), h * 0.10f, DIM, 0.18f))
        if (event == null) {
            c.drawText("Nothing scheduled", pad, h * 0.58f, paint(body(context), h * 0.16f, LIGHT))
            return bmp
        }
        // colored spine
        val spine = Paint().apply { color = event.color.toArgb(); isAntiAlias = true }
        c.drawRoundRect(pad, h * 0.34f, pad + w * 0.012f, h * 0.78f, 6f, 6f, spine)
        val textX = pad + w * 0.05f
        c.drawText(ellipsize(event.title, (w - textX - pad), paint(body(context), h * 0.17f, WHITE)), textX, h * 0.52f, paint(body(context), h * 0.17f, WHITE))
        c.drawText(CalendarFormats.timeLabelFor(event), textX, h * 0.72f, paint(ndot(context), h * 0.12f, LIGHT))
        val mins = ChronoUnit.MINUTES.between(now, event.start)
        val cd = when {
            mins < 0 -> "now"
            mins < 60 -> "in ${mins}m"
            mins <= 60 * 48 -> "in ${mins / 60}h"
            else -> null
        }
        if (cd != null) {
            c.drawText(cd, w - pad, h * 0.24f, paint(ndot(context), h * 0.12f, accent(context), align = Paint.Align.RIGHT))
        }
        return bmp
    }

    fun renderAgenda(context: Context, w: Int, h: Int, events: List<EventItem>): Bitmap {
        val (bmp, c) = bitmap(w, h)
        val pad = w * 0.07f
        labelWithDot(c, context, "TODAY", pad, h * 0.16f, paint(mono(context), h * 0.075f, DIM, 0.18f))
        if (events.isEmpty()) {
            c.drawText("All clear", pad, h * 0.5f, paint(body(context), h * 0.11f, LIGHT))
            return bmp
        }
        val rowH = h * 0.19f
        var y = h * 0.34f
        events.take(4).forEach { e ->
            val timePaint = paint(ndot(context), h * 0.085f, LIGHT)
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
        val pad = w * 0.09f

        // Header: month name (dot-matrix caps) left, year right — same rhythm as the app's header bar.
        c.drawText(
            CalendarFormats.MON_FULL[today.monthValue - 1],
            pad, h * 0.125f,
            paint(ndotCaps(context), h * 0.075f, WHITE, 0.05f),
        )
        c.drawText(
            today.year.toString(),
            w - pad, h * 0.125f,
            paint(mono(context), h * 0.06f, DIM, 0.12f, Paint.Align.RIGHT),
        )

        val weekStart = Prefs(context).weekStart // honors the app's first-day setting
        val cellW = (w - pad * 2) / 7f

        // Day-of-week letters, dot-matrix caps.
        val dowPaint = paint(ndotCaps(context), h * 0.044f, DIM, 0.08f, Paint.Align.CENTER)
        for (i in 0 until 7) {
            c.drawText(
                CalendarFormats.DOW_SHORT[(weekStart + i) % 7],
                pad + cellW * i + cellW / 2f, h * 0.235f, dowPaint,
            )
        }

        // Only this month's days — no dimmed spill-over; blank cells keep it clean.
        val first = today.withDayOfMonth(1)
        val offset = (CalendarFormats.dowIndex(first) - weekStart + 7) % 7
        val daysInMonth = today.lengthOfMonth()
        val rows = (offset + daysInMonth + 6) / 7
        val gridTop = h * 0.285f
        val cellH = (h - gridTop - h * 0.045f) / rows
        val numPaint = paint(ndot(context), (cellH * 0.42f).coerceAtMost(cellW * 0.46f), WHITE, align = Paint.Align.CENTER)

        for (day in 1..daysInMonth) {
            val idx = offset + day - 1
            val cx = pad + cellW * (idx % 7) + cellW / 2f
            val cy = gridTop + cellH * (idx / 7) + cellH / 2f
            val baseline = cy - (numPaint.ascent() + numPaint.descent()) / 2f
            val date = first.plusDays((day - 1).toLong())
            val isToday = date == today
            if (isToday) {
                val fill = Paint().apply { color = accent(context); isAntiAlias = true }
                c.drawCircle(cx, cy, minOf(cellW, cellH) * 0.44f, fill)
            }
            numPaint.color = if (isToday) Color.BLACK else WHITE
            c.drawText(day.toString(), cx, baseline, numPaint)
            if (!isToday && date in eventDays) {
                val dot = Paint().apply { color = DIM; isAntiAlias = true }
                c.drawCircle(cx, cy + cellH * 0.34f, minOf(cellW, cellH) * 0.06f, dot)
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
