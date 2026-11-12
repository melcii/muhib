package app.muhib

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * A compass ring that turns with the phone. The Kaaba sits on the ring at the Qibla bearing,
 * and the fixed mark at the top is where the phone points.
 */
class QiblaDial(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val dp = resources.displayMetrics.density
    private val accent = Accent.color(context)
    private val ink = context.getColor(R.color.ink)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; color = context.getColor(R.color.hairline) }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * dp; strokeCap = Paint.Cap.ROUND; color = context.getColor(R.color.muted) }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f * dp; strokeCap = Paint.Cap.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13 * dp
        color = context.getColor(R.color.muted)
        typeface = Fonts.medium(context)
    }
    private val box = RectF()
    private val pointer = Path()

    /**
     * Where the phone points, degrees from true north. The dial doesn't jump there: each frame it
     * glides part of the way, so sensor noise turns into a calm, continuous movement.
     */
    var heading = Float.NaN
        set(value) {
            field = value
            if (shown.isNaN()) shown = value
            if (!gliding) { gliding = true; lastFrame = 0L; postInvalidateOnAnimation() }
        }

    /** The angle actually drawn, easing toward [heading]. */
    private var shown = Float.NaN
    private var gliding = false
    private var lastFrame = 0L

    var qibla = 0f
        set(value) { field = value; invalidate() }

    val facing get() = !shown.isNaN() && abs(((qibla - shown) % 360 + 540) % 360 - 180) < 4

    /** Moves [shown] toward [heading] by how much time passed: smooth at any frame rate. */
    private fun glide() {
        if (heading.isNaN()) return
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
        lastFrame = now
        val diff = ((heading - shown) % 360 + 540) % 360 - 180
        // About 90% of the way in a quarter second; the short way round.
        val step = diff * (1f - kotlin.math.exp(-dt / GLIDE))
        shown = (shown + step + 360) % 360
        if (abs(diff) > 0.05f) postInvalidateOnAnimation() else gliding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), (280 * dp).toInt())
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        glide()
        val heading = if (shown.isNaN()) 0f else shown
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 22 * dp
        val on = facing

        // The phone's own direction: a small fixed pointer above the ring.
        pointer.reset()
        pointer.moveTo(cx, cy - r - 16 * dp)
        pointer.lineTo(cx - 6 * dp, cy - r - 6 * dp)
        pointer.lineTo(cx + 6 * dp, cy - r - 6 * dp)
        pointer.close()
        fillPaint.color = if (on) accent else ink
        canvas.drawPath(pointer, fillPaint)

        canvas.save()
        canvas.rotate(-heading, cx, cy)
        canvas.drawCircle(cx, cy, r, ringPaint)
        for (deg in 0 until 360 step 15) {
            val long = deg % 90 == 0
            canvas.save()
            canvas.rotate(deg.toFloat(), cx, cy)
            canvas.drawLine(cx, cy - r + (if (long) 10 else 5) * dp, cx, cy - r + 1 * dp, tick)
            canvas.restore()
        }
        // North label, kept upright.
        canvas.save()
        canvas.rotate(heading, cx, cy - r + 26 * dp)
        canvas.drawText("N", cx, cy - r + 31 * dp, text)
        canvas.restore()

        canvas.rotate(qibla, cx, cy)
        needle.color = if (on) accent else ink
        canvas.drawLine(cx, cy, cx, cy - r + 26 * dp, needle)
        fillPaint.color = accent
        canvas.drawCircle(cx, cy, 5 * dp, fillPaint)
        // The Kaaba: a small dark cube with a gold band, on the ring.
        val k = if (on) 13 * dp else 11 * dp
        box.set(cx - k, cy - r - k, cx + k, cy - r + k)
        fillPaint.color = 0xFF1B1F1D.toInt()
        canvas.drawRoundRect(box, 3 * dp, 3 * dp, fillPaint)
        fillPaint.color = 0xFFD4AF6A.toInt()
        canvas.drawRect(cx - k, cy - r - k * 0.45f, cx + k, cy - r - k * 0.15f, fillPaint)
        canvas.restore()
    }

    private companion object {
        /** Seconds for the dial to cover about two thirds of the way to a new reading. */
        const val GLIDE = 0.11f
    }
}
