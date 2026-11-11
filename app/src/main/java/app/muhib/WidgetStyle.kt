package app.muhib

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * How a widget looks: background colour, how see-through it is, and the text colour.
 * Either every widget shares one style, or each placed widget keeps its own (the user chooses).
 * [AUTO] as the colour follows the phone's light or dark mode.
 */
data class WidgetStyle(val color: Int, val opacity: Int, val text: Int) {

    val alpha get() = (opacity * 255 / 100).coerceIn(0, 255)

    /** The colour actually drawn: [AUTO] becomes the card colour of the current mode. */
    fun background(context: Context) = if (color == AUTO) context.getColor(R.color.card) else color or 0xFF000000.toInt()

    /** Light text on dark or see-through backgrounds, unless the user chose. */
    fun lightText(context: Context): Boolean = when (text) {
        TEXT_LIGHT -> true
        TEXT_DARK -> false
        else -> if (color == AUTO) night(context) && opacity >= 40 || opacity < 40 else opacity < 40 || luminance(color) < 0.5
    }

    fun textColor(context: Context) = if (lightText(context)) 0xFFF4F5F2.toInt() else 0xFF1B1F1D.toInt()

    fun dim(context: Context) = (textColor(context) and 0x00FFFFFF) or ((if (lightText(context)) 0xB3 else 0x99) shl 24)

    /** The current time stands out in the accent, or in full white/ink where the accent would clash. */
    fun highlight(context: Context): Int {
        val accent = if (lightText(context)) Accent.brighter(Accent.dark(context)) else Accent.light(context)
        return if (opacity >= 40 && Accent.contrast(accent, background(context)) < 2.2) textColor(context) else accent
    }

    /** Saves for one widget, or for all when styles are shared (or [id] is none). */
    fun save(context: Context, id: Int = NONE) {
        val suffix = if (shared(context) || id == NONE) "" else "_$id"
        Store.prefs(context).edit()
            .putInt(KEY_COLOR + suffix, color).putInt(KEY_OPACITY + suffix, opacity).putInt(KEY_TEXT + suffix, text).apply()
    }

    companion object {
        const val NONE = AppWidgetManager.INVALID_APPWIDGET_ID
        const val TEXT_AUTO = 0
        const val TEXT_LIGHT = 1
        const val TEXT_DARK = 2
        /** Not a real colour (fully transparent near-black): means "follow light/dark mode". */
        const val AUTO = 0x00000001
        const val GREEN = 0xFF2F6B55.toInt()

        val swatches = intArrayOf(
            AUTO, 0xFFFFFFFF.toInt(), 0xFFF6F5F1.toInt(), 0xFFE6EFEA.toInt(),
            GREEN, 0xFF1E2A44.toInt(), 0xFF000000.toInt(),
        )

        private const val KEY_SHARED = "w_shared"
        private const val KEY_COLOR = "w_color"
        private const val KEY_OPACITY = "w_opacity"
        private const val KEY_TEXT = "w_text"

        fun shared(context: Context) = Store.prefs(context).getBoolean(KEY_SHARED, true)

        /** Switching to "each its own" starts every widget from the shared style. */
        fun setShared(context: Context, on: Boolean) = Store.prefs(context).edit().putBoolean(KEY_SHARED, on).apply()

        fun load(context: Context, id: Int = NONE): WidgetStyle {
            val p = Store.prefs(context)
            val suffix = if (!shared(context) && id != NONE && p.contains(KEY_COLOR + "_$id")) "_$id" else ""
            return WidgetStyle(
                p.getInt(KEY_COLOR + suffix, AUTO), p.getInt(KEY_OPACITY + suffix, 100), p.getInt(KEY_TEXT + suffix, TEXT_AUTO),
            )
        }

        /** Forgets the styles of widgets that were removed. */
        fun forget(context: Context, ids: IntArray) = Store.prefs(context).edit().apply {
            ids.forEach { id -> listOf(KEY_COLOR, KEY_OPACITY, KEY_TEXT).forEach { remove(it + "_$id") } }
        }.apply()

        private fun night(context: Context) =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        private fun luminance(c: Int) =
            (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255
    }
}
