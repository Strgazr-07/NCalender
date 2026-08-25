package com.ncalendar.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.ncalendar.app.R
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.Prefs
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Draws the home-screen widgets to bitmaps so we can use Nothing's Ndot / NType fonts
 * (RemoteViews can't set custom typefaces directly). Minimal and spaced; colors come from
 * [WidgetPalette] so widgets follow the app's light/dark/system theme setting.
 */
object WidgetRenderer {

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

    /**
     * Basis for TEXT SIZES (positions still use h directly, so nothing shifts). Every text size
     * used to scale off height alone, which on a narrow-but-tall widget produced text sized for
     * the long dimension crammed into the short one — the "text is too big to read the whole
     * event name" complaint. Taking the smaller dimension leaves normal wide widgets untouched
     * and only pulls the narrow ones back.
     */
    private fun sizeRef(w: Int, h: Int): Float = minOf(w, h).toFloat()

    private fun bitmap(w: Int, h: Int, p: WidgetPalette): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply { color = p.bg; isAntiAlias = true }
        val r = h * 0.14f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, bg)
        // Hairline border, same card language as the app's surfaces.
        val border = Paint().apply {
            color = p.border; isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, border)
        return bmp to canvas
    }

    /** Draws the app's "accent dot + mono label" motif. */
    private fun labelWithDot(c: Canvas, text: String, x: Float, baselineY: Float, paint: Paint, p: WidgetPalette) {
        val r = paint.textSize * 0.24f
        val dot = Paint().apply { color = p.accent; isAntiAlias = true }
        c.drawCircle(x + r, baselineY - paint.textSize * 0.32f, r, dot)
        c.drawText(text, x + r * 2f + paint.textSize * 0.5f, baselineY, paint)
    }

    private fun paint(tf: Typeface, size: Float, color: Int, spacing: Float = 0f, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint().apply {
            isAntiAlias = true
            typeface = tf
            textSize = size
            this.color = color
            letterSpacing = spacing
            textAlign = align
        }

    fun renderDotDate(context: Context, w: Int, h: Int): Bitmap {
        val p = WidgetPalette.resolve(context)
        val (bmp, c) = bitmap(w, h, p)
        val today = LocalDate.now()
        val ref = sizeRef(w, h)
        val pad = w * 0.11f
        val weekday = CalendarFormats.DOW[CalendarFormats.dowIndex(today)]
        val month = CalendarFormats.MON_FULL[today.monthValue - 1]

        labelWithDot(c, weekday, pad, h * 0.185f, paint(ndotCaps(context), ref * 0.095f, p.textPrimary, 0.1f), p)

        // The date, big and horizontally centered (NType82 — the app's default face).
        val numPaint = paint(body(context), ref * 0.42f, p.textPrimary, 0f, Paint.Align.CENTER)
        val centerY = h * 0.55f
        val baseline = centerY - (numPaint.ascent() + numPaint.descent()) / 2f
        c.drawText(today.dayOfMonth.toString(), w / 2f, baseline, numPaint)

        c.drawText(
            "$month ${today.year}",
            w / 2f, h * 0.915f,
            paint(mono(context), ref * 0.07f, p.textPrimary, 0.14f, Paint.Align.CENTER),
        )
        return bmp
    }

    fun renderNextEvent(context: Context, w: Int, h: Int, event: EventItem?, now: LocalDateTime): Bitmap {
        val p = WidgetPalette.resolve(context)
        val (bmp, c) = bitmap(w, h, p)
        val ref = sizeRef(w, h)
        val pad = w * 0.08f
        // An event already in progress (its start is in the past) needs a countdown to its
        // END, not its start — counting down to a past instant used to just read "now" for
        // the entire duration of whatever was currently happening.
        val ongoing = event != null && !event.start.isAfter(now) && event.end.isAfter(now)
        labelWithDot(c, if (ongoing) "HAPPENING NOW" else "NEXT EVENT", pad, h * 0.24f, paint(mono(context), ref * 0.10f, p.textDim, 0.18f), p)
        if (event == null) {
            c.drawText("Nothing scheduled", pad, h * 0.58f, paint(body(context), ref * 0.16f, p.textSecondary))
            return bmp
        }
        // Countdown first — the title wrap below needs to know how much room it can't have.
        val cdPaint = paint(ndot(context), ref * 0.12f, p.accent, align = Paint.Align.RIGHT)
        // No upper cap: the previous version hid the countdown entirely past 48 hours, so the
        // widget's most useful corner just went blank for anything further out than tomorrow.
        val cd = if (ongoing) CalendarFormats.remaining(now, event.end, compact = true) else CalendarFormats.countdown(now, event.start, compact = true)
        c.drawText(cd, w - pad, h * 0.24f, cdPaint)

        val spine = Paint().apply { color = event.color.toArgb(); isAntiAlias = true }
        c.drawRoundRect(pad, h * 0.34f, pad + w * 0.012f, h * 0.78f, 6f, 6f, spine)
        val textX = pad + w * 0.05f
        val titleW = (w - textX - pad).toInt().coerceAtLeast(1)
        // This is the only widget with the vertical room for two lines (the title band spans
        // 0.34..0.78 of the height), so it's the only one that wraps rather than shrinking to
        // a single ellipsized line.
        val titlePaint = paint(body(context), ref * 0.155f, p.textPrimary)
        val layout = StaticLayout.Builder.obtain(event.title, 0, event.title.length, titlePaint, titleW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        c.save()
        // StaticLayout draws from the top of its line box, not a baseline.
        c.translate(textX, h * 0.40f)
        layout.draw(c)
        c.restore()
        fitText(c, CalendarFormats.timeLabelFor(event), textX, h * 0.80f, w - textX - pad, paint(ndot(context), ref * 0.11f, p.textSecondary))
        return bmp
    }

    /** The "UPCOMING" label above Split's scrollable event list — just the label; the list
     *  itself is a real ListView (SplitUpcomingFactory), not part of this bitmap. */
    fun renderUpcomingHeader(context: Context, w: Int, h: Int): Bitmap {
        val p = WidgetPalette.resolve(context)
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ref = sizeRef(w, h)
        labelWithDot(c, "UPCOMING", 0f, h * 0.75f, paint(mono(context), ref * 0.42f, p.textDim, 0.16f), p)
        return bmp
    }

    fun renderAgenda(context: Context, w: Int, h: Int, events: List<EventItem>): Bitmap {
        val p = WidgetPalette.resolve(context)
        val (bmp, c) = bitmap(w, h, p)
        val ref = sizeRef(w, h)
        val pad = w * 0.07f
        labelWithDot(c, "TODAY", pad, h * 0.16f, paint(mono(context), ref * 0.075f, p.textDim, 0.18f), p)
        if (events.isEmpty()) {
            c.drawText("All clear", pad, h * 0.5f, paint(body(context), ref * 0.11f, p.textSecondary))
            return bmp
        }
        val rowH = h * 0.19f
        var y = h * 0.34f
        val timePaint = paint(ndot(context), ref * 0.085f, p.textSecondary)
        // Content-sized rather than a flat 20% of the width — on a small widget that fixed
        // column was eating room the title badly needed, leaving ~12 characters for it.
        val timeColW = (timePaint.measureText("00:00") + w * 0.03f).coerceAtMost(w * 0.20f)
        events.take(4).forEach { e ->
            c.drawText(if (e.allDay) "ALL" else CalendarFormats.fmtTime(e.start.toLocalTime()), pad, y, timePaint)
            val barX = pad + timeColW
            val bar = Paint().apply { color = e.color.toArgb(); isAntiAlias = true }
            c.drawRoundRect(barX, y - h * 0.10f, barX + w * 0.012f, y + h * 0.02f, 4f, 4f, bar)
            val titleX = barX + w * 0.045f
            fitText(c, e.title, titleX, y, w - titleX - pad, paint(body(context), ref * 0.10f, p.textPrimary))
            y += rowH
        }
        return bmp
    }

    /**
     * One row for the scrollable list widget, in the app's "This Week" day-grouped language: a
     * day column (weekday + big number) drawn ONLY on the first event of each date ([day] ==
     * null otherwise), so a day with several events reads as one dated block rather than
     * repeating itself. Flat/transparent — the list container draws the rounded card.
     */
    fun renderUpcomingRow(context: Context, w: Int, h: Int, e: EventItem, day: LocalDate?, today: LocalDate): Bitmap {
        val p = WidgetPalette.resolve(context)
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = w * 0.05f
        val dayColW = w * 0.19f
        if (day != null) {
            val isToday = day == today
            val cx = pad + dayColW / 2f
            // The weekday stays neutral; only the number carries "today", and as a filled disc
            // rather than accent-colored text. The accent is user-selectable and can be dark,
            // and dark text drawn straight onto this widget's own dark background isn't
            // guaranteed to read at all — a disc with contrast-computed onAccent text always
            // is, which is the same treatment the month grid gives its own today cell.
            c.drawText(
                CalendarFormats.DOW[CalendarFormats.dowIndex(day)],
                cx, h * 0.32f,
                paint(mono(context), h * 0.17f, p.textDim, 0.06f, Paint.Align.CENTER),
            )
            val numCy = h * 0.68f
            if (isToday) {
                c.drawCircle(cx, numCy, h * 0.26f, Paint().apply { color = p.accent; isAntiAlias = true })
            }
            val numPaint = paint(ndot(context), h * 0.36f, if (isToday) p.onAccent else p.textPrimary, align = Paint.Align.CENTER)
            c.drawText(day.dayOfMonth.toString(), cx, numCy - (numPaint.ascent() + numPaint.descent()) / 2f, numPaint)
        }
        val ex = pad + dayColW
        val dotR = h * 0.05f
        c.drawCircle(ex + dotR, h * 0.40f, dotR, Paint().apply { color = e.color.toArgb(); isAntiAlias = true })
        val tx = ex + dotR * 2.5f + w * 0.02f
        fitText(c, e.title, tx, h * 0.46f, w - tx - pad, paint(body(context), h * 0.26f, p.textPrimary))
        fitText(c, CalendarFormats.timeLabelFor(e), tx, h * 0.76f, w - tx - pad, paint(ndot(context), h * 0.19f, p.textSecondary))
        // Divider under the event only (not the day column), so days read as blocks.
        c.drawLine(ex, h - 1.5f, w - pad, h - 1.5f, strokePaint(p.border, 1.5f))
        return bmp
    }

    /**
     * The entire month calendar — title, weekday row and all six week rows — as ONE bitmap.
     *
     * This replaced a GridView whose cells were each their own bitmap. Per-cell rendering meant
     * every cell had to GUESS the size the GridView would hand it, which is why the day numbers
     * were repeatedly too small or cramped: the guess was wrong, and it was wrong differently
     * for MiniMonth (full width) than for Split (a ~half-width column). Drawing the whole grid
     * at once means the column width is known exactly, so type is sized against it directly and
     * looks identical at any widget size. Taps come from a transparent overlay of real views
     * (widget_month_overlay.xml) layered on top, so dates stay individually clickable.
     */
    fun renderMonthCalendar(
        context: Context,
        w: Int,
        h: Int,
        eventDays: Set<LocalDate>,
        today: LocalDate,
    ): Bitmap {
        val p = WidgetPalette.resolve(context)
        val bw = w.coerceAtLeast(1)
        val bh = h.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // The grid spans the full width so a column is exactly 1/7th — the same fraction the
        // overlay's tap targets use, which is what keeps touch aligned with what's drawn.
        val colW = bw / 7f
        val gridTop = bh * GRID_TOP_FRACTION
        val rowH = (bh - gridTop) / 6f

        // Type scales off the column width, the natural unit of a calendar: it makes the grid
        // read the same whether it's the whole widget or Split's narrower left column.
        val inset = colW * 0.12f
        c.drawText(
            CalendarFormats.MON_FULL[today.monthValue - 1],
            inset, bh * 0.13f,
            paint(ndotCaps(context), colW * 0.78f, p.textPrimary, 0.05f),
        )
        c.drawText(
            today.year.toString(),
            bw - inset, bh * 0.13f,
            paint(mono(context), colW * 0.46f, p.textDim, 0.10f, Paint.Align.RIGHT),
        )

        val weekStart = Prefs(context).weekStart
        val dowPaint = paint(ndotCaps(context), colW * 0.40f, p.textDim, 0.08f, Paint.Align.CENTER)
        for (i in 0 until 7) {
            c.drawText(
                CalendarFormats.DOW_SHORT[(weekStart + i) % 7],
                colW * i + colW / 2f, bh * 0.235f, dowPaint,
            )
        }

        val cells = CalendarFormats.monthGridDates(today, weekStart)
        val discR = minOf(colW, rowH) * 0.42f
        val dotR = minOf(colW, rowH) * 0.07f
        val numPaint = paint(ndot(context), colW * 0.74f, p.textPrimary, align = Paint.Align.CENTER)
        cells.forEachIndexed { index, date ->
            val inMonth = date.monthValue == today.monthValue && date.year == today.year
            // Only this month's days are drawn — the leading/trailing spill-over from the
            // neighbouring months is left blank (its cell also gets no tap target in
            // MonthCalendarWidget), so the grid reads as one month rather than a run of
            // numbers that restarts twice.
            if (!inMonth) return@forEachIndexed
            val cx = colW * (index % 7) + colW / 2f
            val cy = gridTop + rowH * (index / 7) + rowH / 2f
            // Room for the event dot is reserved in EVERY cell, not just the ones that have an
            // event. Nudging up only the dotted days (as this used to) put them on a different
            // baseline from their neighbours and made every row look crooked.
            val numCy = cy - rowH * 0.07f
            val isToday = date == today
            if (isToday) {
                // Centred on the number, not the cell, so the disc reads as sitting around the
                // digit rather than slightly below it.
                c.drawCircle(cx, numCy, discR, Paint().apply { color = p.accent; isAntiAlias = true })
            }
            numPaint.color = if (isToday) p.onAccent else p.textPrimary
            c.drawText(
                date.dayOfMonth.toString(),
                cx, numCy - (numPaint.ascent() + numPaint.descent()) / 2f, numPaint,
            )
            if (date in eventDays) {
                c.drawCircle(
                    cx, cy + rowH * 0.33f, dotR,
                    Paint().apply { color = if (isToday) p.onAccent else p.textDim; isAntiAlias = true },
                )
            }
        }
        return bmp
    }

    /** Share of the calendar bitmap's height taken by the month title + weekday row. MUST match
     *  widget_month_overlay.xml's leading spacer weight (28 against 6 rows of 12). */
    const val GRID_TOP_FRACTION = 0.28f

    private fun strokePaint(color: Int, width: Float) = Paint().apply {
        this.color = color; isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = width
    }

    /** How far below the requested size text may shrink before it stops being legible and
     *  ellipsis is the better trade. */
    private const val MIN_FIT_SCALE = 0.72f

    /**
     * Draws [text] at [x],[baselineY], shrinking the type up to [MIN_FIT_SCALE] to fit
     * [maxWidth] before falling back to an ellipsis. The old helper only ever truncated, so a
     * long event title lost its tail even when a slightly smaller size would have shown all of
     * it — the "reduce the text so the whole name fits" report.
     */
    private fun fitText(c: Canvas, text: String, x: Float, baselineY: Float, maxWidth: Float, p: TextPaint) {
        if (maxWidth <= 0f) return
        val original = p.textSize
        val floor = original * MIN_FIT_SCALE
        while (p.measureText(text) > maxWidth && p.textSize > floor) {
            p.textSize = (p.textSize * 0.94f).coerceAtLeast(floor)
        }
        c.drawText(ellipsize(text, maxWidth, p), x, baselineY, p)
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
