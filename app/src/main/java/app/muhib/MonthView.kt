package app.muhib

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** A month grid: each day with its Hijri date beneath; tapping one picks it. */
class MonthView(private val context: Context, private val grid: LinearLayout, private val onPick: (LocalDate) -> Unit) {
    private val locale = Locale(Lang.current(context))
    private val firstDay: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
    private val dp = context.resources.displayMetrics.density

    fun show(month: YearMonth, selected: LocalDate, today: LocalDate) {
        grid.removeAllViews()
        grid.addView(row().apply {
            for (i in 0 until 7) addView(cell().apply {
                addView(text(firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale), 11f, context.getColor(R.color.muted)))
                minimumHeight = (28 * dp).toInt()
            })
        })
        val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        var date = month.atDay(1).minusDays(lead.toLong())
        while (date <= month.atEndOfMonth()) {
            val r = row()
            for (i in 0 until 7) {
                val d = date
                r.addView(cell().apply {
                    if (d.month == month.month) {
                        val picked = d == selected
                        val h = Hijri.of(d)
                        val ink = when {
                            picked -> context.getColor(R.color.paper)
                            d == today -> Accent.color(context)
                            else -> context.getColor(R.color.ink)
                        }
                        addView(text(d.dayOfMonth.toString(), 15f, ink, bold = d == today || picked))
                        addView(text(h[0].toString(), 10f, if (picked) context.getColor(R.color.paper) else context.getColor(R.color.muted)))
                        if (picked) setBackgroundResource(R.drawable.day_selected).also { Accent.paint(this) }
                        else setBackgroundResource(R.drawable.day_bg)
                        setOnClickListener { onPick(d) }
                        contentDescription = d.toString()
                    }
                })
                date = date.plusDays(1)
            }
            grid.addView(r)
        }
    }

    private fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    private fun cell() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = (50 * dp).toInt()
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = (2 * dp).toInt()
            setMargins(m, m, m, m)
        }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = s
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        typeface = context.resources.getFont(if (bold) R.font.inter_medium else R.font.inter)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

}
