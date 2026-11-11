package app.muhib

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

object Fmt {
    private fun locale(context: Context) = Locale(Lang.current(context))

    /** A clock time in the place's own zone. [short] drops AM/PM, for the widget. */
    fun time(context: Context, ms: Long, zone: ZoneId, short: Boolean = false): String {
        val pattern = when {
            DateFormat.is24HourFormat(context) -> "HH:mm"
            short -> "h:mm"
            else -> "h:mm a"
        }
        return DateTimeFormatter.ofPattern(pattern, locale(context)).format(Instant.ofEpochMilli(ms).atZone(zone))
    }

    /** "1h 24m" or "24 min", rounded up so it never says 0 before the time comes. */
    fun left(context: Context, ms: Long): String {
        val minutes = ceil(ms / 60_000.0).toInt().coerceAtLeast(0)
        return if (minutes >= 60) context.getString(R.string.left_hm, minutes / 60, minutes % 60)
        else context.getString(R.string.left_m, minutes)
    }

    fun prayer(context: Context, index: Int): String = context.resources.getStringArray(R.array.prayers)[index]

    fun hijri(context: Context, day: Day): String {
        val month = context.resources.getStringArray(R.array.hijri_months)[(day.hijriMonth - 1).coerceIn(0, 11)]
        return "${day.hijriDay} $month ${day.hijriYear}"
    }

    /** "just now", "12 min ago", "3 h ago", "2 days ago". */
    fun ago(context: Context, at: Long): String {
        val minutes = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 2 -> context.getString(R.string.just_now)
            minutes < 60 -> context.getString(R.string.minutes_ago, minutes.toInt())
            minutes < 48 * 60 -> context.getString(R.string.hours_ago, (minutes / 60).toInt())
            else -> context.getString(R.string.days_ago, (minutes / 1440).toInt())
        }
    }

    /** A location's accuracy: "15 m" or "2.4 km". */
    fun distance(context: Context, metres: Float): String =
        if (metres < 1000) context.getString(R.string.metres, metres.toInt().coerceAtLeast(1))
        else context.getString(R.string.kilometres, String.format(locale(context), "%.1f", metres / 1000))

    fun date(context: Context, date: LocalDate): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale(context)).format(date)
}
