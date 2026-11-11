package app.muhib

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** A thin line that fills as time passes, with small marks (thirds and the middle, for the night). */
class NightBar(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val dp = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.hairline); strokeCap = Paint.Cap.ROUND; strokeWidth = 4 * dp }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Accent.color(context); strokeCap = Paint.Cap.ROUND; strokeWidth = 4 * dp }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.muted); strokeCap = Paint.Cap.ROUND; strokeWidth = 1.5f * dp }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.card) }

    var marks = floatArrayOf()
        set(value) { field = value; invalidate() }

    /** 0..1, or negative to hide the fill and dot. */
    var progress = -1f
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (22 * dp).toInt())

    override fun onDraw(canvas: Canvas) {
        val pad = 8 * dp
        val y = height / 2f
        val w = width - 2 * pad
        canvas.drawLine(pad, y, pad + w, y, track)
        marks.forEach { canvas.drawLine(pad + w * it, y - 7 * dp, pad + w * it, y + 7 * dp, mark) }
        if (progress >= 0) {
            val x = pad + w * progress
            canvas.drawLine(pad, y, x, y, fill)
            canvas.drawCircle(x, y, 7 * dp, ring)
            canvas.drawCircle(x, y, 5 * dp, fill.apply { style = Paint.Style.FILL })
        }
    }
}
