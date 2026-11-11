package app.muhib

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/** A phone tracing a figure-8, the motion that recalibrates a compass. Animates only while shown. */
class Figure8(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val dp = resources.displayMetrics.density
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * dp; strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.hairline)
    }
    private val phone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Accent.color(context) }
    private val path = Path()
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    private fun point(t: Float, out: FloatArray) {
        // A lemniscate: x = sin t, y = sin t cos t.
        val w = width / 2f - 14 * dp
        val h = height / 2f - 10 * dp
        out[0] = width / 2f + w * sin(t)
        out[1] = height / 2f + h * 2 * sin(t) * cos(t)
    }

    override fun onDraw(canvas: Canvas) {
        val p = FloatArray(2)
        path.reset()
        for (i in 0..64) {
            point((i / 64f * 2 * Math.PI).toFloat(), p)
            if (i == 0) path.moveTo(p[0], p[1]) else path.lineTo(p[0], p[1])
        }
        canvas.drawPath(path, trail)
        point(phase, p)
        canvas.save()
        canvas.rotate(sin(phase) * 25, p[0], p[1])
        canvas.drawRoundRect(p[0] - 7 * dp, p[1] - 12 * dp, p[0] + 7 * dp, p[1] + 12 * dp, 3 * dp, 3 * dp, phone)
        canvas.restore()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) anim.start() else anim.cancel()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }
}
