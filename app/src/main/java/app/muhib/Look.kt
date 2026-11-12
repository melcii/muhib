package app.muhib

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * The app wearing the widget's colours. When on (the default) and a widget has its own
 * background colour, the app's cards take that colour and its page a slightly deeper shade.
 * Text follows: a dark widget puts the app in its dark look, a light one in its light look.
 * With "Auto", or when turned off in Settings, the app follows the phone as usual.
 */
object Look {
    private const val KEY = "match_widget"

    fun on(context: Context) = Store.prefs(context).getBoolean(KEY, true)
    fun setOn(context: Context, on: Boolean) = Store.prefs(context).edit().putBoolean(KEY, on).apply()

    /**
     * The widget style the app follows: the shared one, or with each widget its own, the first
     * placed widget (the combined one first). Null when off, or the style is Auto.
     */
    fun style(context: Context): WidgetStyle? {
        if (!on(context)) return null
        val style = if (WidgetStyle.shared(context)) WidgetStyle.load(context)
        else Widgets.placed(context).sortedBy { (kind, _) -> if (kind == Widgets.COMBINED) 0 else 1 + kind }
            .firstOrNull()?.let { WidgetStyle.load(context, it.second) } ?: WidgetStyle.load(context)
        return style.takeIf { it.color != WidgetStyle.AUTO }
    }

    private fun dark(style: WidgetStyle) = when (style.text) {
        WidgetStyle.TEXT_LIGHT -> true
        WidgetStyle.TEXT_DARK -> false
        else -> Accent.contrast(style.color or 0xFF000000.toInt(), Color.WHITE) > Accent.contrast(style.color or 0xFF000000.toInt(), Color.BLACK)
    }

    /** Changes whenever the app's look does: screens compare it to know when to redraw. */
    fun key(context: Context) = style(context)?.let { "${it.color}:${it.text}" } ?: "-"

    /** A context in the widget's light or dark look; used by every screen. */
    fun wrap(context: Context): Context {
        val style = style(context) ?: return context
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark(style)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return context.createConfigurationContext(config)
    }

    /** The cards' colour: the widget's background. */
    fun card(context: Context): Int = style(context)?.let { it.color or 0xFF000000.toInt() } ?: context.getColor(R.color.card)

    /** The page behind the cards: a touch deeper than them, as in the usual look. */
    fun paper(context: Context): Int {
        val style = style(context) ?: return context.getColor(R.color.paper)
        val c = style.color or 0xFF000000.toInt()
        val f = if (dark(style)) 0.72f else 0.95f
        return Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    }

    /** Status and navigation bars in the page colour, with icons that stay readable. */
    fun window(activity: Activity) {
        if (style(activity) == null) return
        val paper = paper(activity)
        @Suppress("DEPRECATION")
        activity.window.apply {
            statusBarColor = paper
            navigationBarColor = paper
        }
        val light = Accent.contrast(paper, Color.BLACK) > Accent.contrast(paper, Color.WHITE)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(if (light) bars else 0, bars)
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = if (light)
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        }
    }
}
