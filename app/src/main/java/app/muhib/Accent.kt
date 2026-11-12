package app.muhib

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * The accent colour of the app and widget: one of a few presets or the user's own.
 * Layouts and drawables are written with the green from resources; [paint] swaps it for the chosen
 * colour after they are inflated. A custom colour keeps its hue, and its lightness is moved just
 * enough to stay readable on light and dark backgrounds.
 */
object Accent {
    class Preset(val name: Int, val light: Int, val dark: Int)

    val presets = listOf(
        Preset(R.string.accent_green, 0xFF2F6B55.toInt(), 0xFF8FCBAE.toInt()),
        Preset(R.string.accent_blue, 0xFF2E5E8C.toInt(), 0xFF9EC0E6.toInt()),
        Preset(R.string.accent_plum, 0xFF8A3D62.toInt(), 0xFFE5A9C6.toInt()),
        Preset(R.string.accent_amber, 0xFF8A6418.toInt(), 0xFFE2BE72.toInt()),
    )

    const val CUSTOM = -1
    private const val KEY = "accent"
    private const val KEY_CUSTOM = "accent_custom"

    fun choice(context: Context) = Store.prefs(context).getInt(KEY, 0)
    fun custom(context: Context) = Store.prefs(context).getInt(KEY_CUSTOM, 0xFF6A4C93.toInt())

    fun set(context: Context, choice: Int, custom: Int? = null) = Store.prefs(context).edit().apply {
        putInt(KEY, choice)
        if (custom != null) putInt(KEY_CUSTOM, custom)
    }.apply()

    /** Changes whenever the accent does: screens compare it to know when to redraw. */
    fun key(context: Context) = "${choice(context)}:${custom(context)}:${Look.key(context)}:${Fonts.choice(context)}"

    fun night(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** The accent on a light background. */
    fun light(context: Context) = presets.getOrNull(choice(context))?.light ?: forLight(custom(context))

    /** The accent on a dark background. */
    fun dark(context: Context) = presets.getOrNull(choice(context))?.dark ?: forDark(custom(context))

    fun color(context: Context) = when {
        choice(context) == 0 -> context.getColor(R.color.accent)
        night(context) -> dark(context)
        else -> light(context)
    }

    /** A faint tint of the accent for selected rows and notes. */
    fun soft(context: Context): Int {
        if (choice(context) == 0) return context.getColor(R.color.accent_soft)
        val h = hsl(color(context))
        return if (night(context)) fromHsl(h[0], minOf(h[1], 0.21f), 0.13f) else fromHsl(h[0], minOf(h[1], 0.24f), 0.92f)
    }

    fun forLight(c: Int) = hsl(c).let { fromHsl(it[0], it[1], it[2].coerceIn(0.18f, 0.40f)) }
    fun forDark(c: Int) = hsl(c).let { fromHsl(it[0], it[1], it[2].coerceIn(0.66f, 0.82f)) }

    /** Slightly lighter than [forDark], for text over a wallpaper. */
    fun brighter(c: Int) = hsl(c).let { fromHsl(it[0], it[1], (it[2] + 0.06f).coerceAtMost(0.88f)) }

    // ---- painting views ----

    /**
     * Recolours everything under [root] that was drawn in the resource green, and, when the app
     * wears the widget's colours ([Look]), the cards and the page as well.
     */
    fun paint(root: View) {
        val c = root.context
        val from = c.getColor(R.color.accent)
        val swaps = mapOf(
            from to color(c),
            c.getColor(R.color.accent_soft) to soft(c),
            c.getColor(R.color.card) to Look.card(c),
            c.getColor(R.color.paper) to Look.paper(c),
        ).filter { (a, b) -> a != b }
        if (swaps.isEmpty()) return
        walk(root, from, color(c), swaps)
    }

    private fun walk(v: View, from: Int, to: Int, swaps: Map<Int, Int>) {
        if (v is TextView && v.currentTextColor == from) v.setTextColor(to)
        if (v is ImageView && v.tag == "accent") v.imageTintList = ColorStateList.valueOf(to)
        if (v is Switch) {
            // The theme's unchecked thumb colour isn't a public attribute: Material's own values.
            val thumb = if (night(v.context)) 0xFFBDBDBD.toInt() else 0xFFF1F1F1.toInt()
            v.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(to, thumb))
            v.trackTintList = checkedList(v.context, to, android.R.attr.colorForeground)
        }
        if (v is EditText) {
            val a = v.context.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlNormal))
            v.backgroundTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(to, a.getColor(0, Color.GRAY)))
            a.recycle()
            if (android.os.Build.VERSION.SDK_INT >= 29) v.textCursorDrawable?.setTint(to)
        }
        if (v is SeekBar) {
            v.progressTintList = ColorStateList.valueOf(to)
            v.thumbTintList = ColorStateList.valueOf(to)
        }
        v.background?.let { recolor(it.mutate(), swaps) }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), from, to, swaps)
    }

    private fun recolor(d: Drawable, swaps: Map<Int, Int>) {
        when (d) {
            is GradientDrawable -> d.color?.defaultColor?.let { swaps[it] }?.let { d.setColor(it) }
            is android.graphics.drawable.ColorDrawable -> swaps[d.color]?.let { d.color = it }
            is LayerDrawable -> for (i in 0 until d.numberOfLayers) recolor(d.getDrawable(i), swaps)
            is DrawableContainer -> (d.constantState as? DrawableContainer.DrawableContainerState)?.let { s ->
                for (i in 0 until s.childCount) s.getChild(i)?.let { recolor(it, swaps) }
            }
        }
    }

    /** The accent when checked, else the theme's own colour for [normalAttr]. */
    private fun checkedList(context: Context, accent: Int, normalAttr: Int): ColorStateList {
        val a = context.obtainStyledAttributes(intArrayOf(normalAttr))
        val normal = a.getColor(0, Color.GRAY)
        a.recycle()
        return ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, normal))
    }

    /** For icons drawn in the accent from code: tinted when [on] and the accent isn't the default. */
    fun tint(v: ImageView, on: Boolean) {
        v.imageTintList = if (on && choice(v.context) != 0) ColorStateList.valueOf(color(v.context)) else null
    }

    /** Shows a dialog with its buttons and check marks in the accent. */
    fun show(builder: AlertDialog.Builder): AlertDialog {
        val d = builder.create()
        val c = d.context
        val to = color(c)
        if (choice(c) != 0) {
            d.listView?.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {
                    if (child is CheckedTextView) child.checkMarkTintList = checkedList(c, to, android.R.attr.colorControlNormal)
                }
                override fun onChildViewRemoved(parent: View, child: View) {}
            })
            d.setOnShowListener {
                listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
                    .forEach { d.getButton(it)?.setTextColor(to) }
            }
        }
        d.show()
        return d
    }

    // ---- colour maths ----

    fun hsl(c: Int): FloatArray {
        val r = Color.red(c) / 255f
        val g = Color.green(c) / 255f
        val b = Color.blue(c) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        val d = max - min
        if (d == 0f) return floatArrayOf(0f, 0f, l)
        val s = d / (1 - Math.abs(2 * l - 1))
        val h = when (max) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return floatArrayOf((h + 360) % 360, s.coerceIn(0f, 1f), l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1 - Math.abs(2 * l - 1)) * s
        val x = c * (1 - Math.abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when ((h / 60).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.rgb(((r + m) * 255).toInt().coerceIn(0, 255), ((g + m) * 255).toInt().coerceIn(0, 255), ((b + m) * 255).toInt().coerceIn(0, 255))
    }

    /** WCAG contrast ratio between two opaque colours. */
    fun contrast(a: Int, b: Int): Double {
        fun lum(c: Int): Double {
            fun ch(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
        }
        val x = lum(a)
        val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
