package app.muhib

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Background colour, opacity and text colour for the widget, with a live preview over a
 * stand-in wallpaper. Also opens when the widget is first placed or reconfigured.
 */
class WidgetStyleActivity : Activity() {
    private lateinit var style: WidgetStyle
    /** The widget being styled, or none for the shared style. */
    private var target = WidgetStyle.NONE
    private var kind = Widgets.PRAYER

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Look.wrap(Lang.wrap(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.apply(this)
        setContentView(R.layout.activity_widget_style)
        Accent.paint(window.decorView)
        Look.window(this)
        // Placing the widget succeeds even if this screen is just closed.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            findViewById<View>(R.id.scroll).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        target = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetId else intent.getIntExtra(EXTRA_EDIT, WidgetStyle.NONE)
        kind = if (target != WidgetStyle.NONE) Widgets.kindOf(this, target) else Widgets.placed(this).firstOrNull()?.first ?: Widgets.PRAYER
        style = WidgetStyle.load(this, target)
        findViewById<TextView>(R.id.styleFor).text = when {
            WidgetStyle.shared(this) -> getString(R.string.widgets_style_all)
            else -> getString(Widgets.name(kind))
        }

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.done).setOnClickListener { finish() }

        val swatches = findViewById<LinearLayout>(R.id.swatches)
        WidgetStyle.swatches.forEach { color ->
            swatches.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
                tag = color
                setOnClickListener { update(style.copy(color = color)) }
            })
        }
        // Last: any colour you like.
        swatches.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
            contentDescription = getString(R.string.accent_custom)
            setOnClickListener {
                ColorPicker.show(this@WidgetStyleActivity, R.string.background_pick, style.color) { update(style.copy(color = it)) }
            }
        })

        findViewById<SeekBar>(R.id.opacity).apply {
            max = 100
            progress = style.opacity
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) update(style.copy(opacity = value))
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }

        listOf(R.id.textAuto to WidgetStyle.TEXT_AUTO, R.id.textLight to WidgetStyle.TEXT_LIGHT, R.id.textDark to WidgetStyle.TEXT_DARK)
            .forEach { (id, v) -> findViewById<View>(id).setOnClickListener { update(style.copy(text = v)) } }

        // Fonts: one for the app and every widget, so picking one here redraws this screen too.
        val fonts = findViewById<LinearLayout>(R.id.fonts)
        val current = Fonts.choice(this)
        Fonts.all.forEachIndexed { i, font ->
            fonts.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(dp(18), dp(10), dp(18), dp(10))
                minimumWidth = dp(84)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(Look.card(context))
                    setStroke(dp(if (i == current) 2 else 1), if (i == current) Accent.color(context) else getColor(R.color.hairline))
                }
                contentDescription = getString(font.name)
                addView(TextView(context).apply {
                    setText(R.string.font_aa)
                    textSize = 26f
                    typeface = Fonts.sample(context, i)
                    setTextColor(getColor(R.color.ink))
                    gravity = android.view.Gravity.CENTER
                })
                addView(TextView(context).apply {
                    setText(font.name)
                    textSize = 11f
                    maxLines = 1
                    typeface = Fonts.sample(context, i)
                    setTextColor(if (i == current) Accent.color(context) else getColor(R.color.muted))
                    gravity = android.view.Gravity.CENTER
                })
                setOnClickListener {
                    if (i == current) return@setOnClickListener
                    Fonts.set(this@WidgetStyleActivity, i)
                    Widgets.refreshAll(applicationContext)
                    recreate()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }

        findViewById<View>(R.id.exactLink).setOnClickListener { openExactAlarmSettings(this) }
        render()
        if (savedInstanceState == null) Motion.stagger(Motion.children(findViewById<ViewGroup>(R.id.page)), step = 60)
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(R.id.exactLink).visibility = if (Widgets.exact(this)) View.GONE else View.VISIBLE
        Widgets.refreshAll(applicationContext)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun update(next: WidgetStyle) {
        style = next
        style.save(this, target)
        Widgets.refreshAll(applicationContext)
        render()
    }

    private fun render() {
        // The real widget, drawn by the same code the home screen uses.
        val frame = findViewById<FrameLayout>(R.id.preview)
        frame.removeAllViews()
        val view = Widgets.build(this, kind, target, style).apply(applicationContext, frame)
        val height = when (kind) { Widgets.COMBINED -> 190; Widgets.NEXT -> 72; Widgets.REMINDER -> 120; else -> 116 }
        frame.addView(view, FrameLayout.LayoutParams(if (kind == Widgets.NEXT) dp(170) else ViewGroup.LayoutParams.MATCH_PARENT, dp(height)))

        val swatches = findViewById<LinearLayout>(R.id.swatches)
        for (i in 0 until swatches.childCount) {
            val v = swatches.getChildAt(i)
            val preset = v.tag as Int?
            val custom = style.color !in WidgetStyle.swatches
            val selected = if (preset == null) custom else preset == style.color
            v.background = ColorPicker.swatch(this, preset ?: style.color.takeIf { custom }, selected)
            v.scaleX = if (selected) 1f else 0.86f
            v.scaleY = v.scaleX
        }
        findViewById<TextView>(R.id.opacityValue).text = getString(R.string.opacity_value, style.opacity)
        listOf(R.id.textAuto to WidgetStyle.TEXT_AUTO, R.id.textLight to WidgetStyle.TEXT_LIGHT, R.id.textDark to WidgetStyle.TEXT_DARK)
            .forEach { (id, v) ->
                findViewById<TextView>(id).apply {
                    isSelected = style.text == v
                    setTextColor(getColor(if (style.text == v) R.color.ink else R.color.muted))
                }
            }
    }

    companion object {
        /** Which widget to style when opened from Settings; none means the shared style. */
        const val EXTRA_EDIT = "edit"
    }
}
