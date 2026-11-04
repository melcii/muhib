package app.muhib

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator

/** Small, quiet motion shared by every screen: things arrive one by one, and covered things breathe. */
object Motion {
    val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun children(group: ViewGroup): List<View> = (0 until group.childCount).map { group.getChildAt(it) }

    /** Fades and lifts each visible view in, one after another. */
    fun stagger(views: List<View>, delay: Long = 0, step: Long = 110, rise: Float = 16f) {
        views.filter { it.visibility == View.VISIBLE }.forEachIndexed { i, v ->
            v.animate().cancel()
            v.alpha = 0f
            v.translationY = rise * v.resources.displayMetrics.density
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay + i * step)
                .setDuration(700)
                .setInterpolator(ease)
                .start()
        }
    }

    /** A gentle arrival for a large surface: fade with a slight settle in scale. */
    fun settle(view: View, delay: Long = 0) {
        view.animate().cancel()
        view.alpha = 0f
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay).setDuration(800).setInterpolator(ease).start()
    }

    fun breathe(view: View, from: Float = 0.94f, to: Float = 1.06f, period: Long = 3200): Animator =
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, from, to),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, from, to),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 1f),
        ).apply {
            duration = period
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

    /** A slow rise and settle of a surface's shadow, so a covered card softly glows. */
    fun lift(view: View, to: Float = 14f, period: Long = 3000): Animator =
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Z, 0f, to * view.resources.displayMetrics.density).apply {
            duration = period
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

    fun spin(view: View, period: Long = 28000): Animator =
        ObjectAnimator.ofFloat(view, View.ROTATION, view.rotation, view.rotation + 360f).apply {
            duration = period
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

    /** An expanding, fading ring, like a ripple on still water. */
    fun ripple(view: View, delay: Long = 0, period: Long = 2600): Animator =
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.9f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.9f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0f),
        ).apply {
            duration = period
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            interpolator = PathInterpolator(0.1f, 0.6f, 0.3f, 1f)
        }
}
