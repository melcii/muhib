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

    /** Where the phone points, degrees from true north. */
    var heading = 0f
        set(value) { field = value; invalidate() }

    var qibla = 0f
        set(value) { field = value; invalidate() }

    val facing get() = abs(((qibla - heading) % 360 + 540) % 360 - 180) < 4

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), (280 * dp).toInt())
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
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
}
