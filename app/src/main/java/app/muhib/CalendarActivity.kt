package app.muhib

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Any day's prayer times: a month of days with their Hijri dates, and the picked day's six
 * times plus Ishrâq, Duha and the night. Swipe the month sideways to go back or forward.
 */
class CalendarActivity : Activity() {
    private val month by lazy { MonthView(this, findViewById(R.id.monthGrid)) { pick(it) } }
    private var shown: YearMonth? = null
    private var picked: LocalDate? = null
    private var flungAt = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        Accent.paint(window.decorView)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            findViewById<View>(R.id.scroll).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.prevMonth).setOnClickListener { turn(-1) }
        findViewById<View>(R.id.nextMonth).setOnClickListener { turn(1) }
        findViewById<View>(R.id.today).setOnClickListener {
            val place = Places.selected(this) ?: return@setOnClickListener
            val today = LocalDate.now(Times.get(this, place).zone)
            val step = YearMonth.from(today).compareTo(shown)
            picked = today
            shown = YearMonth.from(today)
            render()
            slide(step.coerceIn(-1, 1))
        }
        swipeMonths()
        if (Places.selected(this) == null) return finish()
        render()
        if (savedInstanceState == null) Motion.stagger(Motion.children(findViewById(R.id.page)), step = 50)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun render() {
        val place = Places.selected(this) ?: return
        val schedule = Times.get(this, place)
        val today = LocalDate.now(schedule.zone)
        val day = picked ?: today.also { picked = it }
        val month = shown ?: YearMonth.from(day).also { shown = it }
        val locale = java.util.Locale(Lang.current(this))
        findViewById<TextView>(R.id.placeLabel).text = place.name.ifEmpty { getString(R.string.current_location) }
        findViewById<TextView>(R.id.monthTitle).text =
            java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) }
        val first = schedule.day(month.atDay(1))
        val last = schedule.day(month.atEndOfMonth())
        val months = resources.getStringArray(R.array.hijri_months)
        findViewById<TextView>(R.id.monthHijri).text = if (first.hijriMonth == last.hijriMonth) "${months[first.hijriMonth - 1]} ${first.hijriYear}"
        else "${months[first.hijriMonth - 1]} – ${months[last.hijriMonth - 1]} ${last.hijriYear}"
        this.month.show(month, day, today)
        findViewById<View>(R.id.today).visibility = if (day == today && month == YearMonth.from(today)) View.INVISIBLE else View.VISIBLE

        val d = schedule.day(day)
        val zone = schedule.zone
        findViewById<TextView>(R.id.dayTitle).text = Fmt.date(this, day)
        findViewById<TextView>(R.id.dayHijri).text = Fmt.hijri(this, d)
        val now = System.currentTimeMillis()
        val m = Moment(schedule, now)
        val current = if (day == today) m.current else -1
        val next = if (day == today && m.nextIndex < 6) m.nextIndex else -1
        val group = findViewById<LinearLayout>(R.id.dayGroup)
        group.removeAllViews()
        for (i in 0..5) {
            val note = when (i) {
                current -> getString(R.string.now)
                next -> getString(R.string.compact_in, Fmt.left(this, d.t[i] - now))
                else -> null
            }
            row(group, Fmt.prayer(this, i), Fmt.time(this, d.t[i], zone), note, i == current, day == today && d.t[i] <= now && i != current)
        }

        // Ishrâq, Duha and the night, which runs from this Maghrib to the next Fajr.
        val following = schedule.day(day.plusDays(1))
        val (rise, _, _) = Moment(schedule, d.imsak).forbidden
        val ishraq = rise.second
        fun night(x: Double) = d.maghrib + ((following.imsak - d.maghrib) * x).toLong()
        val extra = findViewById<LinearLayout>(R.id.extraGroup)
        extra.removeAllViews()
        listOf(
            getString(R.string.ishraq) to ishraq,
            getString(R.string.duha) to ishraq + 20 * Moment.MIN,
            getString(R.string.night_third_1) to night(1 / 3.0),
            getString(R.string.midnight) to night(0.5),
            getString(R.string.night_third_2) to night(2 / 3.0),
            getString(R.string.seher) to night(5 / 6.0),
        ).forEach { (name, at) -> row(extra, name, Fmt.time(this, at, zone), null, false, at <= now) }
    }

    /** One line of a times list: a dot for the current one, muted once it has passed. */
    private fun row(group: ViewGroup, name: String, time: String, note: String?, highlight: Boolean, past: Boolean) {
        val ink = getColor(R.color.ink)
        val muted = getColor(R.color.muted)
        if (group.childCount > 0) group.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.hairline))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(38) }
        })
        val r = LayoutInflater.from(this).inflate(R.layout.row_time, group, false)
        Accent.paint(r)
        r.findViewById<View>(R.id.dot).visibility = if (highlight) View.VISIBLE else View.INVISIBLE
        r.findViewById<TextView>(R.id.name).apply { text = name; setTextColor(if (past) muted else ink) }
        r.findViewById<TextView>(R.id.time).apply { text = time; setTextColor(if (past) muted else ink) }
        r.findViewById<TextView>(R.id.note).text = note.orEmpty()
        group.addView(r)
    }

    private fun pick(date: LocalDate) {
        // A swipe that ended on a day is a swipe, not a tap.
        if (System.currentTimeMillis() - flungAt < 400) return
        picked = date
        render()
        Motion.stagger(listOf(findViewById(R.id.dayTitle), findViewById(R.id.dayHijri), findViewById(R.id.dayGroup),
            findViewById(R.id.extraLabel), findViewById(R.id.extraGroup)), step = 35, rise = 6f)
    }

    private fun turn(step: Int) {
        shown = shown?.plusMonths(step.toLong())
        render()
        slide(step)
    }

    /** The new month slides in from the side it came from. */
    private fun slide(step: Int) {
        if (step == 0) return
        val grid = findViewById<View>(R.id.monthGrid)
        grid.translationX = step * dp(40).toFloat()
        grid.alpha = 0f
        grid.animate().translationX(0f).alpha(1f).setDuration(320).setInterpolator(Motion.ease).start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun swipeMonths() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) < abs(vy) * 1.5f || abs(vx) < 600) return false
                flungAt = System.currentTimeMillis()
                turn(if (vx < 0) 1 else -1)
                return true
            }
        })
        findViewById<View>(R.id.monthGrid).setOnTouchListener { _, e -> detector.onTouchEvent(e); false }
        // The day cells take the touches, so listen on each as well.
        findViewById<ViewGroup>(R.id.monthGrid).setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                if (child is ViewGroup) for (i in 0 until child.childCount) child.getChildAt(i).setOnTouchListener { _, e -> detector.onTouchEvent(e); false }
            }
            override fun onChildViewRemoved(parent: View, child: View) {}
        })
    }
}
