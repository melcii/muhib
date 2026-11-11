package app.muhib

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate

/**
 * Short notifications before each prayer time and when it comes. Only one alarm is ever set:
 * the next notification. When it fires, it posts and sets the one after.
 */
object PrayerAlerts {
    private const val KEY_ON = "notify"
    private const val KEY_BEFORE = "notify_before"
    private const val KEY_AT = "notify_at"
    private const val KEY_WHICH = "notify_which"
    private const val CHANNEL = "prayers"
    const val ACTION = "app.muhib.REMIND"

    /** Minutes before a time that can be chosen; 0 turns the early note off. */
    val beforeChoices = intArrayOf(0, 5, 10, 15, 20, 30, 45, 60)

    fun on(context: Context) = Store.prefs(context).getBoolean(KEY_ON, true)
    fun setOn(context: Context, on: Boolean) = Store.prefs(context).edit().putBoolean(KEY_ON, on).apply()
    fun before(context: Context) = Store.prefs(context).getInt(KEY_BEFORE, 20)
    fun setBefore(context: Context, minutes: Int) = Store.prefs(context).edit().putInt(KEY_BEFORE, minutes).apply()
    fun atTime(context: Context) = Store.prefs(context).getBoolean(KEY_AT, true)
    fun setAtTime(context: Context, on: Boolean) = Store.prefs(context).edit().putBoolean(KEY_AT, on).apply()

    /** Which of the six times notify, as bits 0..5. Sunrise is off at first: it ends Fajr rather than starting a prayer. */
    fun which(context: Context) = Store.prefs(context).getInt(KEY_WHICH, 0b111101)
    fun setWhich(context: Context, bits: Int) = Store.prefs(context).edit().putInt(KEY_WHICH, bits).apply()

    fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Where notifications are for: where you are, or else the place on screen. */
    private fun place(context: Context) = Places.here(context) ?: Places.selected(context)

    private fun intent(context: Context, extras: Intent.() -> Unit = {}) = PendingIntent.getBroadcast(
        context, 2, Intent(context, PrayerAlertReceiver::class.java).setAction(ACTION).apply(extras),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets the alarm for the next notification, or clears it when there is none to give. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val place = place(context)
        if (!on(context) || place == null || (before(context) == 0 && !atTime(context)) || which(context) == 0) {
            am.cancel(intent(context))
            return
        }
        val schedule = Times.get(context, place)
        val now = System.currentTimeMillis()
        val before = before(context) * Moment.MIN
        val which = which(context)
        var best: Triple<Long, Int, Boolean>? = null
        val today = LocalDate.now(schedule.zone)
        for (offset in 0L..2L) {
            val day = schedule.day(today.plusDays(offset))
            for (i in 0..5) {
                if (which and (1 shl i) == 0) continue
                val t = day.t[i]
                if (before > 0 && t - before > now && (best == null || t - before < best.first)) best = Triple(t - before, i, true)
                if (atTime(context) && t > now && (best == null || t < best.first)) best = Triple(t, i, false)
            }
            if (best != null) break
        }
        val (at, index, early) = best ?: return am.cancel(intent(context))
        val pending = intent(context) {
            putExtra("index", index)
            putExtra("early", early)
            putExtra("time", if (early) at + before else at)
        }
        // Waking the phone is the point here. Exact when allowed; otherwise Android may run it a few minutes late.
        if (Widgets.exact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /** Posts the note: "Asr in 20 min" before, "Asr time" when it comes. Each one clears itself. */
    fun post(context: Context, index: Int, early: Boolean, time: Long) {
        val now = System.currentTimeMillis()
        // Too late to be useful (the phone was off, say): skip it.
        if (early && now >= time) return
        if (!early && now - time > 15 * Moment.MIN) return
        val place = place(context) ?: return
        if (!allowed(context)) return
        val c = Lang.wrap(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val zone = Times.get(context, place).zone
        val name = Fmt.prayer(c, index)
        val title = if (early) c.getString(R.string.widget_left, name, Fmt.left(c, time - now))
        else c.getString(R.string.notif_at, name)
        val where = place.name.ifEmpty { c.getString(R.string.current_location) }
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setColor(Accent.light(context))
            .setContentTitle(title)
            .setContentText(Fmt.time(c, time, zone) + " · " + where)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setShowWhen(false)
            // Brief: the early note goes when the time comes; the on-time note after 15 minutes.
            .setTimeoutAfter(if (early) time - now else 15 * Moment.MIN)
            .build()
        nm.notify(2, n)
    }
}

class PrayerAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PrayerAlerts.ACTION) {
            PrayerAlerts.post(context, intent.getIntExtra("index", 0), intent.getBooleanExtra("early", false), intent.getLongExtra("time", 0))
        }
        PrayerAlerts.schedule(context)
    }
}
