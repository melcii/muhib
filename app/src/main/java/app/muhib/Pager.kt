package app.muhib

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pages side by side that follow the finger and settle on the nearest one. Its height glides
 * between the pages' own heights as you swipe, so a short page never leaves a gap.
 */
class Pager(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    /** Where we are, in pages: 1.5 is halfway between the second and third. */
    var position = 0f
        private set

    val page get() = position.roundToInt()

    var onScroll: ((Float) -> Unit)? = null
    var onPage: ((Int) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null
    private var settle: ValueAnimator? = null
    private var heights = IntArray(0)

    fun setPage(index: Int, animate: Boolean) {
        val target = index.coerceIn(0, (childCount - 1).coerceAtLeast(0)).toFloat()
        settle?.cancel()
        if (!animate) return moveTo(target).also { onPage?.invoke(page) }
        settle = ValueAnimator.ofFloat(position, target).apply {
            duration = (260 + 160 * abs(target - position)).toLong().coerceAtMost(520)
            interpolator = Motion.ease
            addUpdateListener { moveTo(it.animatedValue as Float) }
            start()
        }
        onPage?.invoke(target.toInt())
    }

    private fun moveTo(p: Float) {
        position = p
        scrollTo((p * width).roundToInt(), 0)
        onScroll?.invoke(p)
        requestLayout()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val childW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        heights = IntArray(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.measure(childW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            heights[i] = c.measuredHeight
        }
        val h = if (childCount == 0) 0 else {
            val a = position.toInt().coerceIn(0, childCount - 1)
            val b = (a + 1).coerceAtMost(childCount - 1)
            val f = position - a
            (heights[a] + (heights[b] - heights[a]) * f).roundToInt()
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) getChildAt(i).layout(i * w, 0, (i + 1) * w, heights.getOrElse(i) { b - t })
        if (changed) scrollTo((position * w).roundToInt(), 0)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; dragging = false
                velocity?.recycle(); velocity = VelocityTracker.obtain()
                velocity?.addMovement(e)
                // Caught mid-settle: hold it here.
                if (settle?.isRunning == true) { settle?.cancel(); dragging = true; parent.requestDisallowInterceptTouchEvent(true) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                val dx = e.x - downX
                val dy = e.y - downY
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                    dragging = true
                    lastX = e.x
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        velocity?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    if (abs(e.x - downX) > slop) { dragging = true; lastX = e.x; parent.requestDisallowInterceptTouchEvent(true) }
                    return true
                }
                var next = position - (e.x - lastX) / width
                // A little resistance past the first and last page.
                val max = (childCount - 1).toFloat()
                if (next < 0f) next = position - (e.x - lastX) / width * 0.35f
                if (next > max) next = position - (e.x - lastX) / width * 0.35f
                lastX = e.x
                moveTo(next.coerceIn(-0.25f, max + 0.25f))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.computeCurrentVelocity(1000)
                val v = velocity?.xVelocity ?: 0f
                val target = when {
                    !dragging -> page
                    v < -minFling -> kotlin.math.floor(position).toInt() + 1
                    v > minFling -> kotlin.math.ceil(position).toInt() - 1
                    else -> page
                }
                dragging = false
                velocity?.recycle(); velocity = null
                setPage(target, animate = true)
            }
        }
        return true
    }
}

/** A row of dots for a [Pager]; the current one stretches into a pill and slides as you swipe. */
class PageDots(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var count = 0
        set(value) { field = value; requestLayout(); invalidate() }
    var position = 0f
        set(value) { field = value; invalidate() }

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = 6 * dp
    private val gap = 8 * dp
    private val wide = 18 * dp
    var color = Accent.color(context)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (dot + 8 * dp).toInt())

    override fun onDraw(canvas: Canvas) {
        if (count < 2) return
        val total = count * dot + (count - 1) * gap + (wide - dot)
        var x = (width - total) / 2
        val cy = height / 2f
        for (i in 0 until count) {
            // 1 when this is the page, fading to 0 a page away.
            val near = (1f - abs(position - i)).coerceIn(0f, 1f)
            val w = dot + (wide - dot) * near
            paint.color = blend(context.getColor(R.color.muted), color, near)
            paint.alpha = (110 + 145 * near).toInt()
            canvas.drawRoundRect(x, cy - dot / 2, x + w, cy + dot / 2, dot / 2, dot / 2, paint)
            x += w + gap
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t)).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
}
