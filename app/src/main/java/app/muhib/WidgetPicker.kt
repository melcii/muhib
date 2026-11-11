package app.muhib

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Every widget, drawn live by the same code the home screen uses, over a stand-in wallpaper.
 * Tap one and Android asks where to put it.
 */
object WidgetPicker {

    /** Heights of the previews, in dp, roughly as each widget sits on a home screen. */
    private fun height(kind: Int) = when (kind) {
        Widgets.REMINDER -> 120
        Widgets.PRAYER -> 116
        Widgets.COMBINED -> 190
        else -> 72
    }

    fun show(activity: Activity, onPinned: () -> Unit = {}) {
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(4), px(20), px(8))
        }
        var dialog: AlertDialog? = null
        for (kind in Widgets.kinds) {
            list.addView(TextView(activity).apply {
                setText(Widgets.name(kind))
                textSize = 14f
                typeface = activity.resources.getFont(R.font.inter_medium)
                setTextColor(activity.getColor(R.color.ink))
                setPadding(px(4), px(18), 0, px(8))
            })
            val frame = FrameLayout(activity).apply {
                setBackgroundResource(R.drawable.wallpaper)
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                setPadding(px(14), px(14), px(14), px(14))
                isClickable = true
                isFocusable = true
                contentDescription = activity.getString(Widgets.name(kind))
                foreground = activity.getDrawable(android.R.drawable.list_selector_background)
                setOnClickListener {
                    Widgets.pin(activity, kind)
                    dialog?.dismiss()
                    onPinned()
                }
            }
            val preview = Widgets.build(activity, kind, WidgetStyle.NONE).apply(activity.applicationContext, frame)
            val width = if (kind == Widgets.NEXT) px(170) else ViewGroup.LayoutParams.MATCH_PARENT
            frame.addView(preview, FrameLayout.LayoutParams(width, px(height(kind)), Gravity.CENTER))
            // Taps go to the frame, not the widget's own buttons.
            preview.isClickable = false
            disableClicks(preview)
            list.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        list.addView(TextView(activity).apply {
            setText(R.string.widget_pick_note)
            textSize = 12f
            setTextColor(activity.getColor(R.color.muted))
            setPadding(px(4), px(16), px(4), 0)
        })
        Accent.paint(list)
        dialog = Accent.show(AlertDialog.Builder(activity)
            .setTitle(R.string.widget_pick_title)
            .setView(ScrollView(activity).apply { addView(list) })
            .setNegativeButton(R.string.cancel, null))
        Motion.stagger(Motion.children(list), delay = 80, step = 40, rise = 8f)
    }

    private fun disableClicks(v: View) {
        v.setOnClickListener(null)
        v.isClickable = false
        if (v is ViewGroup) for (i in 0 until v.childCount) disableClicks(v.getChildAt(i))
    }
}
