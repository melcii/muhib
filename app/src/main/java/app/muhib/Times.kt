package app.muhib

import android.content.Context
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day's times, in epoch millis. [t] holds the six the app shows:
 * imsak (the start of Fajr), sunrise, dhuhr, asr, maghrib, isha.
 */
class Day(
    val date: LocalDate,
    val t: LongArray,
    val sunset: Long,
    val hijriDay: Int,
    val hijriMonth: Int,
    val hijriYear: Int,
    /** True when the times came from AlAdhan rather than being calculated on the phone. */
    val online: Boolean = false,
) {
    val imsak get() = t[IMSAK]
    val sunrise get() = t[SUNRISE]
    val dhuhr get() = t[DHUHR]
    val maghrib get() = t[MAGHRIB]

    companion object {
        const val IMSAK = 0
        const val SUNRISE = 1
        const val DHUHR = 2
        const val ASR = 3
        const val MAGHRIB = 4
        const val ISHA = 5
    }
}

/** Any day's times for one place, in that place's time zone, worked out when first asked for. */
class Schedule(
    val zone: ZoneId,
    private val lat: Double,
    private val lng: Double,
    private val method: Int,
    private val school: Int,
    private val downloaded: Map<LocalDate, LongArray> = emptyMap(),
) {
    private val days = HashMap<LocalDate, Day>()

    @Synchronized
    fun day(date: LocalDate): Day = days.getOrPut(date) {
        val h = Hijri.of(date)
        downloaded[date]?.let { return@getOrPut Day(date, it.copyOf(6), it[6], h[0], h[1], h[2], online = true) }
        val a = PrayTimes.compute(date, lat, lng, method, school)
        Day(date, a.copyOf(6), a[6], h[0], h[1], h[2])
    }
}

/**
 * Prayer times are calculated on the phone. Days downloaded from AlAdhan (only when the user asks,
 * see [Online]) are used instead while they match the current settings.
 */
object Times {
    private val memo = HashMap<String, Pair<String, Schedule>>()

    @Synchronized
    fun get(context: Context, place: Place): Schedule {
        val method = Store.method(context)
        val school = Store.school(context)
        val zone = place.zone(context)
        val key = "${place.lat},${place.lng}|$method|$school|$zone"
        memo[place.id]?.let { if (it.first == key) return it.second }
        val downloaded = Online.days(context, place, zone, method, school)
        return Schedule(zone, place.lat, place.lng, method, school, downloaded).also { memo[place.id] = key to it }
    }

    @Synchronized
    fun forget(id: String) {
        memo.remove(id)
    }

    @Synchronized
    fun forgetAll() = memo.clear()
}

/** The Islamic date for a day, by the Umm al-Qura calendar. */
object Hijri {
    fun of(date: LocalDate): IntArray {
        val cal = IslamicCalendar(TimeZone.GMT_ZONE)
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = date.toEpochDay() * 86_400_000L + 12 * 3_600_000L
        return intArrayOf(cal.get(IslamicCalendar.DAY_OF_MONTH), cal.get(IslamicCalendar.MONTH) + 1, cal.get(IslamicCalendar.EXTENDED_YEAR))
    }
}
