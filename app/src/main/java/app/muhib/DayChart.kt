package app.muhib

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One day at a glance: each part of the day as a block on a timeline, the night in thirds and
 * halves above the evening, and a line for now. Tap a block to pick it.
 */
class DayChart(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /**
     * A stretch of time. [label] is drawn inside the block when it fits, [name] is the longer caption.
     * [row] is 0 for the day line and 1..3 for the night rows; [low] day blocks sit under the night rows.
     */
    class Part(
        val label: String, val name: String, val from: Long, val to: Long, val kind: Int,
        val row: Int = 0, val low: Boolean = false, val alt: Boolean = false,
    )

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val medium = resources.getFont(R.font.inter_medium)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; color = context.getColor(R.color.ink)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; textSize = 11 * sp; textAlign = Paint.Align.CENTER }
    private val hourText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10 * sp; textAlign = Paint.Align.CENTER; color = context.getColor(R.color.muted)
    }
    private val tick = Paint().apply { color = context.getColor(R.color.muted); strokeWidth = 1 * dp }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Accent.color(context); strokeWidth = 2 * dp }
    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium; textSize = 12 * sp; textAlign = Paint.Align.CENTER; color = context.getColor(R.color.paper)
    }

    private val ink = context.getColor(R.color.ink)
    private val muted = context.getColor(R.color.muted)
    private val accent = Accent.color(context)
    private val soft = Accent.soft(context)
    private val hairline = context.getColor(R.color.hairline)
    private val forbidden = context.getColor(R.color.forbidden)
    private val forbiddenInk = context.getColor(R.color.forbidden_ink)
    private val night = context.getColor(R.color.night)

    private var parts = emptyList<Part>()
    private var start = 0L
    private var end = 1L
    private var now = 0L
    private var zone: ZoneId = ZoneId.systemDefault()
    private var tag = ""
    private var hour24 = true
    private val box = RectF()

    var picked: Part? = null
        set(value) { field = value; invalidate() }

    var onPick: ((Part) -> Unit)? = null

    fun show(parts: List<Part>, start: Long, end: Long, now: Long, zone: ZoneId, tag: String, hour24: Boolean) {
        this.parts = parts
        this.start = start
        this.end = end
        this.now = now
        this.zone = zone
        this.tag = tag
        this.hour24 = hour24
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ((RULER + BAND + TAG_AREA) * dp).toInt())

    private fun x(t: Long) = width * ((t.coerceIn(start, end) - start).toFloat() / (end - start))

    private val bandTop get() = RULER * dp
    private val bandBottom get() = (RULER + BAND) * dp
    private val rowStep get() = (ROW + GAP) * dp

    override fun onDraw(canvas: Canvas) {
        if (parts.isEmpty()) return
        drawHours(canvas)

        for (p in parts) {
            if (p.to <= start || p.from >= end) continue
            val top = when {
                p.row > 0 -> bandTop + (p.row - 1) * rowStep
                p.low -> bandTop + 3 * rowStep
                else -> bandTop
            }
            val bottom = if (p.row > 0) top + ROW * dp else bandBottom
            box.set(x(p.from) + 0.5f * dp, top, x(p.to) - 0.5f * dp, bottom)
            if (box.width() <= 0) continue
            val past = p.to <= now
            val current = now >= p.from && now < p.to
            val (color, label) = when (p.kind) {
                FORBIDDEN -> forbidden.withAlpha(if (past) 110 else 255) to forbiddenInk
                NIGHT -> (if (past) hairline else night.withAlpha(if (p.alt) 150 else 255)) to (if (past) muted else ink)
                else -> when {
                    past -> hairline to muted
                    current -> accent.withAlpha(70) to ink
                    else -> soft to ink
                }
            }
            fill.color = color
            canvas.drawRoundRect(box, 4 * dp, 4 * dp, fill)
            if (p === picked) canvas.drawRoundRect(box, 4 * dp, 4 * dp, outline)
            label(canvas, p.label, box, label)
        }
        drawNow(canvas)
    }

    /** Draws the label across the block if it fits, else up the side, else not at all. */
    private fun label(canvas: Canvas, s: String, r: RectF, color: Int) {
        if (s.isEmpty()) return
        text.color = color
        val tw = text.measureText(s)
        val th = text.textSize
        val cx = r.centerX()
        val cy = r.centerY()
        if (tw + 8 * dp <= r.width() && th + 4 * dp <= r.height()) {
            canvas.drawText(s, cx, cy + th * 0.35f, text)
        } else if (th + 3 * dp <= r.width() && tw + 12 * dp <= r.height()) {
            canvas.save()
            canvas.rotate(-90f, cx, cy)
            canvas.drawText(s, cx, cy + th * 0.35f, text)
            canvas.restore()
        }
    }

    /** Hour ticks along the top, with a number every two hours. */
    private fun drawHours(canvas: Canvas) {
        var z = Instant.ofEpochMilli(start).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val base = bandTop - 3 * dp
        while (z.toInstant().toEpochMilli() < end) {
            val px = x(z.toInstant().toEpochMilli())
            val h = z.hour
            val even = h % 2 == 0
            canvas.drawLine(px, base - (if (even) 5 else 3) * dp, px, base, tick)
            if (even) {
                val s = if (hour24) "%02d".format(h) else (if (h % 12 == 0) 12 else h % 12).toString()
                val half = hourText.measureText(s) / 2
                if (px - half >= 0 && px + half <= width) canvas.drawText(s, px, base - 8 * dp, hourText)
            }
            z = z.plusHours(1)
        }
    }

    /** A line through the chart at the current time, with the current part's name under it. */
    private fun drawNow(canvas: Canvas) {
        if (now < start || now >= end) return
        val px = x(now)
        val tagTop = bandBottom + 6 * dp
        canvas.drawLine(px, bandTop - 2 * dp, px, tagTop, nowPaint)
        canvas.drawCircle(px, bandTop - 2 * dp, 3 * dp, nowPaint)
        if (tag.isEmpty()) return
        val w = tagText.measureText(tag) + 20 * dp
        val left = (px - w / 2).coerceIn(0f, (width - w).coerceAtLeast(0f))
        box.set(left, tagTop, left + w, tagTop + 22 * dp)
        fill.color = accent
        canvas.drawRoundRect(box, 11 * dp, 11 * dp, fill)
        canvas.drawText(tag, box.centerX(), box.centerY() + tagText.textSize * 0.35f, tagText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val t = start + ((event.x / width).coerceIn(0f, 1f) * (end - start)).toLong()
                val offset = event.y - bandTop
                val row = if (offset >= 0 && offset < 3 * rowStep) (offset / rowStep).toInt() + 1 else 0
                val hit = parts.firstOrNull { it.row == row && row > 0 && t >= it.from && t < it.to }
                    ?: parts.firstOrNull { it.row == 0 && t >= it.from && t < it.to }
                hit?.let {
                    picked = it
                    onPick?.invoke(it)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun Int.withAlpha(a: Int) = (this and 0x00FFFFFF) or (a shl 24)

    companion object {
        const val PRAYER = 0
        const val FORBIDDEN = 1
        const val NIGHT = 2

        private const val RULER = 24
        private const val BAND = 172
        private const val ROW = 22
        private const val GAP = 2
        private const val TAG_AREA = 32
    }
}
