package app.muhib

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Every font, each shown on the combined widget as it would look on the home screen.
 * Picking one changes the app and all widgets together.
 */
object FontPicker {

    fun show(activity: Activity, onPick: (Int) -> Unit) {
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val current = Fonts.choice(activity)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(4), px(20), px(8))
        }
        var dialog: AlertDialog? = null
        Fonts.all.forEachIndexed { i, font ->
            val selected = i == current
            list.addView(TextView(activity).apply {
                text = activity.getString(font.name)
                textSize = 17f
                typeface = Fonts.sample(activity, i, Fonts.MEDIUM)
                setTextColor(if (selected) Accent.color(activity) else activity.getColor(R.color.ink))
                setPadding(px(4), px(18), 0, px(8))
            })
            val frame = FrameLayout(activity).apply {
                setBackgroundResource(R.drawable.wallpaper)
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                setPadding(px(12), px(12), px(12), px(12))
                isClickable = true
                isFocusable = true
                contentDescription = activity.getString(font.name)
                foreground = GradientDrawable().apply {
                    cornerRadius = 20 * dp
                    if (selected) setStroke(px(3), Accent.color(activity))
                }
                setOnClickListener {
                    dialog?.dismiss()
                    if (i != current) onPick(i)
                }
            }
            val preview = Widgets.build(activity, Widgets.COMBINED, WidgetStyle.NONE, font = i).apply(activity.applicationContext, frame)
            disableClicks(preview)
            frame.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(190), Gravity.CENTER))
            list.addView(frame)
        }
        list.addView(TextView(activity).apply {
            setText(R.string.font_note)
            textSize = 12f
            setTextColor(activity.getColor(R.color.muted))
            setPadding(px(4), px(16), px(4), 0)
        })
        Accent.paint(list)
        dialog = Accent.show(AlertDialog.Builder(activity)
            .setTitle(R.string.font_title)
            .setView(ScrollView(activity).apply { addView(list) })
            .setNegativeButton(R.string.cancel, null))
        Motion.stagger(Motion.children(list), delay = 80, step = 30, rise = 8f)
    }

    private fun disableClicks(v: android.view.View) {
        v.setOnClickListener(null)
        v.isClickable = false
        if (v is ViewGroup) for (i in 0 until v.childCount) disableClicks(v.getChildAt(i))
    }
}
