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
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Twice a day the widget quietly covers itself again with a new pick: after Fajr and after
 * Maghrib, or at two set times if the user prefers (or no place is known yet).
 * A silent notification goes out at most once every 3 to 5 days.
 */
object Reminders {
    private const val CHANNEL = "quiet"
    private const val NOTIFICATION_ID = 1
    const val ACTION_RENEW = "app.muhib.action.RENEW"
    const val DEFAULT_MORNING = 7 * 60
    const val DEFAULT_NIGHT = 21 * 60

    fun morning(context: Context) = Muhib.prefs(context).getInt(Muhib.KEY_MORNING, DEFAULT_MORNING)
    fun night(context: Context) = Muhib.prefs(context).getInt(Muhib.KEY_NIGHT, DEFAULT_NIGHT)
    fun quietOn(context: Context) = Muhib.prefs(context).getBoolean(Muhib.KEY_QUIET_REMINDERS, true)

    /** True: renew after Fajr and Maghrib. False: at the morning and night times. */
    fun withSun(context: Context) = Muhib.prefs(context).getBoolean(KEY_SUN, true)
    fun setWithSun(context: Context, on: Boolean) = Muhib.prefs(context).edit().putBoolean(KEY_SUN, on).apply()
    private const val KEY_SUN = "renew_sun"

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val renew = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_RENEW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Inexact on purpose: no special alarm permission needed, and a few minutes late is fine.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextSlot(context), renew)
    }

    /** The next renewal: Fajr or Maghrib where you are (or the place on screen), else the set times. */
    fun nextSlot(context: Context): Long {
        val now = System.currentTimeMillis()
        val place = Places.here(context) ?: Places.selected(context)
        if (withSun(context) && place != null) {
            val s = Times.get(context, place)
            val today = java.time.LocalDate.now(s.zone)
            return (0L..2L).flatMap { s.day(today.plusDays(it)).let { d -> listOf(d.t[Day.IMSAK], d.t[Day.MAGHRIB]) } }
                .filter { it > now }.min()
        }
        return listOf(morning(context), night(context)).flatMap { minutes ->
            (0..1).map { dayOffset ->
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, minutes / 60)
                    set(Calendar.MINUTE, minutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }.filter { it > now }.min()
    }

    fun renew(context: Context) {
        if (Muhib.isRevealed(context)) Muhib.coverAgain(context)
        Widgets.refreshAll(context)
        maybeNotify(context)
    }

    private fun maybeNotify(context: Context) {
        val prefs = Muhib.prefs(context)
        val now = System.currentTimeMillis()
        if (!quietOn(context) || now < prefs.getLong(Muhib.KEY_NEXT_NOTIFY, 0)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        // Next one in 3–5 days. Two hours of slack so it lands on that day's slot, not the one after.
        val gap = TimeUnit.DAYS.toMillis((3..5).random().toLong()) - TimeUnit.HOURS.toMillis(2)
        prefs.edit().putLong(Muhib.KEY_NEXT_NOTIFY, now + gap).apply()

        val local = Lang.wrap(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, local.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_muhib)
            .setColor(Accent.light(context))
            .setContentTitle(local.getString(R.string.notification_title))
            .setContentIntent(PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Reminders.ACTION_RENEW) Reminders.renew(context)
        // Boot, app update, clock or timezone change: just re-arm the next slot.
        Reminders.schedule(context)
    }
}
