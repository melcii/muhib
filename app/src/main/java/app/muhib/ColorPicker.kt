package app.muhib

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * A small colour picker: hue, saturation and brightness sliders over strips that show what each
 * one does, a hex field, and a preview. [shown] maps the picked colour to what will actually be
 * drawn (the accent adjusts lightness), so the preview never promises a colour it won't use.
 */
object ColorPicker {

    /** A round swatch; null draws a colour wheel, for "pick your own". */
    fun swatch(context: android.content.Context, color: Int?, selected: Boolean): android.graphics.drawable.Drawable {
        // A circle of fixed size in the middle of the view, however wide the view is.
        val size = (40 * context.resources.displayMetrics.density).toInt()
        return android.graphics.drawable.LayerDrawable(arrayOf(circle(context, color, selected))).apply {
            setLayerSize(0, size, size)
            setLayerGravity(0, Gravity.CENTER)
        }
    }

    private fun circle(context: android.content.Context, color: Int?, selected: Boolean) = GradientDrawable().apply {
        val dp = context.resources.displayMetrics.density
        shape = GradientDrawable.OVAL
        if (color != null) setColor(color)
        else {
            gradientType = GradientDrawable.SWEEP_GRADIENT
            colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f % 360, 0.55f, 0.85f)) }
        }
        setStroke(((if (selected) 3 else 1) * dp).toInt(), if (selected) Accent.color(context) else context.getColor(R.color.hairline))
    }

    fun show(activity: Activity, title: Int, initial: Int, shown: (Int) -> Int = { it }, onPick: (Int) -> Unit) {
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        var editingHex = false

        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(12), px(24), px(4))
        }
        val preview = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = activity.resources.getFont(R.font.inter_medium)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56))
        }
        page.addView(preview)

        val strips = ArrayList<View>()
        val bars = ArrayList<SeekBar>()
        val hex = EditText(activity).apply {
            filters = arrayOf(InputFilter.LengthFilter(7))
            isSingleLine = true
            textSize = 15f
            hint = "#2F6B55"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(8) }
        }

        fun color() = Color.HSVToColor(hsv)

        fun refresh() {
            val c = color()
            val s = shown(c)
            preview.text = activity.getString(R.string.color_sample)
            preview.setTextColor(if (Accent.contrast(s, Color.WHITE) > Accent.contrast(s, Color.BLACK)) Color.WHITE else Color.BLACK)
            preview.background = GradientDrawable().apply { setColor(s); cornerRadius = 14 * dp }
            val full = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))
            (strips[1].background as GradientDrawable).colors = intArrayOf(Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])), Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))
            (strips[2].background as GradientDrawable).colors = intArrayOf(Color.BLACK, full)
            if (!editingHex) hex.setText(String.format("#%06X", c and 0xFFFFFF))
        }

        val labels = intArrayOf(R.string.color_hue, R.string.color_saturation, R.string.color_brightness)
        val max = intArrayOf(359, 100, 100)
        for (i in 0..2) {
            page.addView(TextView(activity).apply {
                setText(labels[i])
                textSize = 13f
                setTextColor(activity.getColor(R.color.muted))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = px(if (i == 0) 18 else 12) }
            })
            val strip = View(activity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    if (i == 0) IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f % 360, 1f, 1f)) } else intArrayOf(0, 0))
                    .apply { cornerRadius = 4 * dp }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(8)).apply { topMargin = px(8) }
            }
            strips += strip
            page.addView(strip)
            val bar = SeekBar(activity).apply {
                this.max = max[i]
                progress = if (i == 0) hsv[0].toInt() else (hsv[i] * 100).toInt()
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(36))
                    .apply { marginStart = -px(16); marginEnd = -px(16) }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        hsv[i] = if (i == 0) value.toFloat() else value / 100f
                        refresh()
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) {}
                })
            }
            bars += bar
            page.addView(bar)
        }
        page.addView(hex)
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (!hex.hasFocus()) return
                val v = s.toString().trim().removePrefix("#")
                if (v.length != 6) return
                val c = v.toLongOrNull(16)?.toInt() ?: return
                Color.colorToHSV(c or 0xFF000000.toInt(), hsv)
                bars[0].progress = hsv[0].toInt()
                bars[1].progress = (hsv[1] * 100).toInt()
                bars[2].progress = (hsv[2] * 100).toInt()
                editingHex = true
                refresh()
                editingHex = false
            }
        })
        refresh()
        Accent.paint(page)

        Accent.show(AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(page)
            .setPositiveButton(R.string.ok) { _, _ -> onPick(color() or 0xFF000000.toInt()) }
            .setNegativeButton(R.string.cancel, null))
    }
}
