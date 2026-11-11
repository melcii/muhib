package app.muhib

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews

/**
 * Every widget Muhib offers, in one place: which kinds there are, how each is drawn, and the
 * minute tick that keeps the prayer countdowns current. The tick is a non-waking alarm, so
 * nothing runs while the screen is off.
 */
object Widgets {
    const val REMINDER = 0
    const val PRAYER = 1
    const val COMBINED = 2
    const val NEXT = 3

    val kinds = listOf(REMINDER, PRAYER, COMBINED, NEXT)

    fun provider(kind: Int): Class<*> = when (kind) {
        REMINDER -> MuhibWidget::class.java
        PRAYER -> PrayerWidget::class.java
        COMBINED -> CombinedWidget::class.java
        else -> NextWidget::class.java
    }

    fun name(kind: Int) = when (kind) {
        REMINDER -> R.string.widget_reminder
        PRAYER -> R.string.widget_prayer
        COMBINED -> R.string.widget_combined
        else -> R.string.widget_next
    }

    fun ids(context: Context, kind: Int): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider(kind)))

    /** Every placed widget, as (kind, id). */
    fun placed(context: Context): List<Pair<Int, Int>> = kinds.flatMap { k -> ids(context, k).map { k to it } }

    fun kindOf(context: Context, id: Int): Int = placed(context).firstOrNull { it.second == id }?.first ?: PRAYER

    fun pinSupported(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun pin(context: Context, kind: Int) =
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, provider(kind)), null, null)

    /** Redraws every widget; keeps the minute tick only while a prayer countdown is on screen. */
    fun refreshAll(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        for (kind in kinds) for (id in ids(context, kind)) m.updateAppWidget(id, build(context, kind, id))
        if (kinds.any { it != REMINDER && ids(context, it).isNotEmpty() }) scheduleTick(context) else cancelTick(context)
    }

    fun build(context: Context, kind: Int, id: Int, style: WidgetStyle = WidgetStyle.load(context, id)): RemoteViews = when (kind) {
        REMINDER -> reminder(context, style)
        PRAYER -> prayer(context, style)
        COMBINED -> combined(context, style)
        else -> next(context, style)
    }

    /** While the widget's city is being located. */
    fun showLocating(context: Context) {
        val c = Lang.wrap(context)
        val m = AppWidgetManager.getInstance(context)
        for ((kind, layout) in listOf(PRAYER to R.layout.widget_prayer, COMBINED to R.layout.widget_combined)) {
            val v = RemoteViews(context.packageName, layout)
            v.setTextViewText(R.id.city, c.getString(R.string.locating))
            m.partiallyUpdateAppWidget(ids(context, kind), v)
        }
    }

    // ---- drawing ----

    private val nameIds = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5)
    private val timeIds = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5)
    private const val ARABIC_MAX_IN_WIDGET = 110

    private fun background(context: Context, v: RemoteViews, style: WidgetStyle) {
        v.setInt(R.id.bg, "setColorFilter", style.background(context))
        v.setInt(R.id.bg, "setImageAlpha", style.alpha)
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun locate(context: Context) = PendingIntent.getActivity(
        context, 1, Intent(context, RefreshActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun reveal(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, MuhibWidget::class.java).setAction(MuhibWidget.ACTION_REVEAL),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The widgets show where you are, or the place on screen if location isn't set. */
    private fun place(context: Context) = Places.here(context) ?: Places.selected(context)

    /** The blurred words, tinted with the accent so the cover matches the app. */
    private fun cover(context: Context, v: RemoteViews, style: WidgetStyle, reminder: Reminder) {
        val ink = style.highlight(context)
        v.setImageViewBitmap(R.id.blur, Blur.render(reminder))
        v.setInt(R.id.blur, "setColorFilter", ink)
        v.setInt(R.id.blur, "setImageAlpha", if (style.lightText(context)) 0x66 else 0x4D)
        v.setInt(R.id.star, "setColorFilter", ink)
        v.setTextColor(R.id.prompt, style.textColor(context))
        v.setTextViewText(R.id.prompt, Lang.wrap(context).getString(R.string.prompt))
    }

    private fun reminder(context: Context, style: WidgetStyle): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_muhib)
        background(context, v, style)
        val reminder = Muhib.current(context)
        val revealed = Muhib.isRevealed(context)
        v.setDisplayedChild(R.id.flipper, if (revealed) 1 else 0)
        if (revealed) {
            // Long Arabic passages stay in the app; the widget shows the meaning.
            val arabic = reminder.arabic?.takeIf { it.length <= ARABIC_MAX_IN_WIDGET }
            v.setTextViewText(R.id.arabic, arabic.orEmpty())
            v.setViewVisibility(R.id.arabic, if (arabic == null) View.GONE else View.VISIBLE)
            v.setTextViewText(R.id.text, reminder.text)
            v.setTextViewText(R.id.source, reminder.source)
            v.setTextColor(R.id.arabic, style.textColor(context))
            v.setTextColor(R.id.text, style.textColor(context))
            v.setTextColor(R.id.source, style.dim(context))
            v.setOnClickPendingIntent(android.R.id.background, openApp(context))
        } else {
            cover(context, v, style, reminder)
            v.setOnClickPendingIntent(android.R.id.background, reveal(context))
        }
        return v
    }

    /** City, countdown and the six times; shared by the prayer and combined widgets. */
    private fun times(context: Context, v: RemoteViews, style: WidgetStyle) {
        val c = Lang.wrap(context)
        v.setInt(R.id.pin, "setColorFilter", style.dim(context))
        v.setTextColor(R.id.city, style.textColor(context))
        val highlight = style.highlight(context)
        v.setTextColor(R.id.left, highlight)
        v.setOnClickPendingIntent(R.id.header, locate(context))
        c.resources.getStringArray(R.array.prayers_short).forEachIndexed { i, n -> v.setTextViewText(nameIds[i], n) }

        val place = place(context)
        val schedule = place?.let { Times.get(context, it) }
        val m = schedule?.let { Moment(it) }
        if (place == null || m == null) {
            v.setTextViewText(R.id.city, c.getString(R.string.app_name))
            v.setTextViewText(R.id.left, c.getString(R.string.widget_no_place))
            for (i in 0..5) {
                v.setTextViewText(timeIds[i], "--:--")
                v.setTextColor(nameIds[i], style.dim(context))
                v.setTextColor(timeIds[i], style.dim(context))
            }
            return
        }
        v.setTextViewText(R.id.city, place.name.ifEmpty { c.getString(R.string.current_location) })
        v.setTextViewText(R.id.left, c.getString(R.string.widget_left, Fmt.prayer(c, m.next), Fmt.left(c, m.nextAt - m.now)))
        val day = m.day!!
        for (i in 0..5) {
            v.setTextViewText(timeIds[i], Fmt.time(c, day.t[i], schedule.zone, short = true))
            // The time we are in stands out; the countdown above already names the next one.
            val now = i == m.previous
            v.setTextColor(nameIds[i], if (now) highlight else style.dim(context))
            v.setTextColor(timeIds[i], if (now) highlight else style.textColor(context))
        }
    }

    private fun prayer(context: Context, style: WidgetStyle): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_prayer)
        background(context, v, style)
        times(context, v, style)
        v.setOnClickPendingIntent(R.id.root, openApp(context))
        return v
    }

    private fun combined(context: Context, style: WidgetStyle): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_combined)
        background(context, v, style)
        times(context, v, style)
        v.setOnClickPendingIntent(R.id.times, openApp(context))
        v.setInt(R.id.rule, "setBackgroundColor", (style.textColor(context) and 0x00FFFFFF) or 0x1F000000)
        val reminder = Muhib.current(context)
        val revealed = Muhib.isRevealed(context)
        v.setDisplayedChild(R.id.flipper, if (revealed) 1 else 0)
        if (revealed) {
            v.setTextViewText(R.id.text, reminder.text)
            v.setTextViewText(R.id.source, reminder.source)
            v.setTextColor(R.id.text, style.textColor(context))
            v.setTextColor(R.id.source, style.dim(context))
            v.setOnClickPendingIntent(R.id.flipper, openApp(context))
        } else {
            cover(context, v, style, reminder)
            v.setOnClickPendingIntent(R.id.flipper, reveal(context))
        }
        return v
    }

    private fun next(context: Context, style: WidgetStyle): RemoteViews {
        val c = Lang.wrap(context)
        val v = RemoteViews(context.packageName, R.layout.widget_next)
        background(context, v, style)
        v.setTextColor(R.id.name, style.dim(context))
        v.setTextColor(R.id.left, style.highlight(context))
        v.setTextColor(R.id.at, style.dim(context))
        v.setOnClickPendingIntent(android.R.id.background, openApp(context))
        val place = place(context)
        val m = place?.let { Moment(Times.get(context, it)) }
        if (place == null || m == null) {
            v.setTextViewText(R.id.name, c.getString(R.string.app_name))
            v.setTextViewText(R.id.left, "--:--")
            v.setTextViewText(R.id.at, c.getString(R.string.widget_no_place))
            return v
        }
        v.setTextViewText(R.id.name, Fmt.prayer(c, m.next))
        v.setTextViewText(R.id.left, Fmt.left(c, m.nextAt - m.now))
        v.setTextViewText(R.id.at, Fmt.time(c, m.nextAt, m.schedule.zone) + " · " + place.name.ifEmpty { c.getString(R.string.current_location) })
        return v
    }

    // ---- the minute tick ----

    const val ACTION_TICK = "app.muhib.TICK"

    private fun tickIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, PrayerWidget::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Whether the countdown may tick exactly on the minute (Android 12+ asks the user once). */
    fun exact(context: Context) =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * At the start of the next minute. RTC (not RTC_WAKEUP): while the phone sleeps nothing runs,
     * and the widget catches up the moment the screen comes on.
     */
    private fun scheduleTick(context: Context) {
        val now = System.currentTimeMillis()
        val next = now - now % 60_000 + 60_000 + 200
        val am = context.getSystemService(AlarmManager::class.java)
        if (exact(context)) am.setExact(AlarmManager.RTC, next, tickIntent(context))
        else am.set(AlarmManager.RTC, next, tickIntent(context))
    }

    private fun cancelTick(context: Context) =
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
}

/** Base for every Muhib widget: draws through [Widgets], forgets the styles of removed ones. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.refreshAll(context)
        Reminders.schedule(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) = WidgetStyle.forget(context, ids)
}

/** City, the six times, and the time left until the next one. Also receives the minute tick. */
class PrayerWidget : BaseWidget() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Widgets.ACTION_TICK, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_EXACT_CHANGED -> {
                Widgets.refreshAll(context)
                if (intent.action != Widgets.ACTION_TICK) {
                    PrayerAlerts.schedule(context)
                    Reminders.schedule(context)
                }
            }
        }
    }

    companion object {
        private const val ACTION_EXACT_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}

/** The next prayer and the day's times, with the reminder underneath. */
class CombinedWidget : BaseWidget()

/** Small: just the next prayer and how long until it. */
class NextWidget : BaseWidget()
