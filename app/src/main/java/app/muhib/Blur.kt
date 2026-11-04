package app.muhib

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Draws the waiting reminder's own words and blurs them into soft shapes, so the
 * covered widget hints that something real is underneath. Widgets can't blur live
 * views, so this is done once into a small bitmap. Only alpha is kept; the layout
 * tints it with @color/blur_ink so it follows light and dark mode.
 */
object Blur {
    private const val W = 520
    private const val H = 260
    private const val SCALE = 4

    fun render(reminder: Reminder): Bitmap {
        val big = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.SERIF
        }
        val pad = 40
        var y = 36f
        reminder.arabic?.let { arabic ->
            paint.textSize = 46f
            y += drawBlock(canvas, arabic, paint, pad, y, maxLines = 2) + 16f
        }
        paint.textSize = 30f
        drawBlock(canvas, reminder.text, paint, pad, y, maxLines = 5)

        val small = Bitmap.createScaledBitmap(big, W / SCALE, H / SCALE, true)
        big.recycle()
        blurAlpha(small, radius = 3, passes = 3)
        return small
    }

    private fun drawBlock(canvas: Canvas, text: String, paint: TextPaint, pad: Int, y: Float, maxLines: Int): Int {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, W - 2 * pad)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .build()
        canvas.save()
        canvas.translate(pad.toFloat(), y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun blurAlpha(bitmap: Bitmap, radius: Int, passes: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val a = IntArray(w * h) { pixels[it] ushr 24 }
        val tmp = IntArray(w * h)
        repeat(passes) {
            boxPass(a, tmp, w, h, radius, horizontal = true)
            boxPass(tmp, a, w, h, radius, horizontal = false)
        }
        for (i in pixels.indices) pixels[i] = (a[i] * 2).coerceAtMost(255) shl 24
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val at = { i: Int ->
                val c = i.coerceIn(0, len - 1)
                if (horizontal) line * w + c else c * w + line
            }
            var sum = 0
            for (i in -r..r) sum += src[at(i)]
            for (i in 0 until len) {
                dst[at(i)] = sum / div
                sum += src[at(i + r + 1)] - src[at(i - r)]
            }
        }
    }
}
