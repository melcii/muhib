package app.muhib

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.TypedValue

/**
 * One font for the app and the widgets. Widgets can only use fonts built into Android, so the
 * choices are those (Inter, the default, stays in the app; widgets show the phone's own sans).
 * In the app a choice is a theme overlay filling three weights; in widgets each text gets a span.
 */
object Fonts {
    class Font(val name: Int, val style: Int, val regular: String?, val light: String?, val medium: String?)

    val all = listOf(
        Font(R.string.font_inter, 0, null, null, null),
        Font(R.string.font_serif, R.style.Font_Serif, "serif", "serif", "serif"),
        Font(R.string.font_airy, R.style.Font_Airy, "sans-serif-light", "sans-serif-thin", "sans-serif"),
        Font(R.string.font_mono, R.style.Font_Mono, "monospace", "monospace", "monospace"),
        Font(R.string.font_playful, R.style.Font_Playful, "casual", "casual", "casual"),
    )

    private const val KEY = "font"

    fun choice(context: Context) = Store.prefs(context).getInt(KEY, 0).coerceIn(all.indices)
    fun set(context: Context, choice: Int) = Store.prefs(context).edit().putInt(KEY, choice).apply()

    /** Lays the chosen font over an activity's theme; call before setContentView. */
    fun apply(activity: Activity) {
        val font = all[choice(activity)]
        if (font.style != 0) activity.theme.applyStyle(font.style, true)
    }

    /** The theme's typeface for one weight, for text drawn or built in code. */
    private fun typeface(context: Context, attr: Int): Typeface {
        val v = TypedValue()
        if (!context.theme.resolveAttribute(attr, v, true)) return Typeface.DEFAULT
        return if (v.type == TypedValue.TYPE_STRING) Typeface.create(v.string.toString(), Typeface.NORMAL)
        else context.resources.getFont(v.resourceId)
    }

    fun regular(context: Context) = typeface(context, R.attr.fontRegular)
    fun medium(context: Context) = typeface(context, R.attr.fontMedium)

    /** What a font looks like, for showing the choices. */
    fun sample(context: Context, choice: Int, weight: Int = REGULAR): Typeface {
        val font = all[choice]
        val family = when (weight) { LIGHT -> font.light; MEDIUM -> font.medium; else -> font.regular }
        return if (family != null) Typeface.create(family, Typeface.NORMAL)
        else context.resources.getFont(when (weight) { LIGHT -> R.font.inter_light; MEDIUM -> R.font.inter_medium; else -> R.font.inter })
    }

    const val REGULAR = 0
    const val LIGHT = 1
    const val MEDIUM = 2

    /** Widget text in the chosen font ([choice] overrides, for previews). Inter keeps the widget's own font. */
    fun span(context: Context, text: CharSequence, weight: Int, choice: Int = choice(context)): CharSequence {
        val font = all[choice]
        val family = when (weight) { LIGHT -> font.light; MEDIUM -> font.medium; else -> font.regular } ?: return text
        return SpannableString(text).apply { setSpan(TypefaceSpan(family), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) }
    }
}
