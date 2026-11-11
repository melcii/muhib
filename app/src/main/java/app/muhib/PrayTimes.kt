package app.muhib

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Prayer times worked out on the phone with the PrayTimes.org formulas (the ones AlAdhan uses),
 * so your location never has to leave the device.
 */
object PrayTimes {
    class Params(val fajr: Double, val isha: Double, val ishaMinutes: Int = 0, val offsets: IntArray = IntArray(6))

    /** Twilight angles per method, by AlAdhan's method numbers. */
    fun params(method: Int) = when (method) {
        2 -> Params(15.0, 15.0)
        5 -> Params(19.5, 17.5)
        4 -> Params(18.5, 0.0, ishaMinutes = 90)
        1 -> Params(18.0, 18.0)
        // Diyanet adds its own safety minutes (temkin) to sunrise, Dhuhr, Asr and Maghrib.
        13 -> Params(18.0, 17.0, offsets = intArrayOf(0, -7, 5, 4, 7, 0))
        else -> Params(18.0, 17.0)
    }

    /**
     * Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha and sunset for [date] at the place, as epoch millis
     * rounded to the minute. [school] is 0 for standard Asr, 1 for Hanafi.
     */
    fun compute(date: LocalDate, lat: Double, lng: Double, method: Int, school: Int): LongArray {
        val p = params(method)
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15 * 24)

        // Local solar hours, refined once from rough guesses as PrayTimes does.
        var fajr = angleTime(jd, lat, p.fajr, 5 / 24.0, ccw = true)
        val sunrise = angleTime(jd, lat, RISE_SET, 6 / 24.0, ccw = true)
        val dhuhr = midDay(jd, 12 / 24.0)
        val asr = asrTime(jd, lat, school + 1.0, asrTime(jd, lat, school + 1.0, 13 / 24.0) / 24)
        val sunset = angleTime(jd, lat, RISE_SET, 18 / 24.0, ccw = false)
        var isha = if (p.ishaMinutes > 0) sunset + p.ishaMinutes / 60.0 else angleTime(jd, lat, p.isha, 18 / 24.0, ccw = false)

        // Far from the equator twilight may never end; cap Fajr and Isha at a share of the night.
        val night = sunrise - sunset + 24
        val fajrPortion = p.fajr / 60 * night
        if (fajr.isNaN() || sunrise - fajr > fajrPortion) fajr = sunrise - fajrPortion
        if (p.ishaMinutes == 0) {
            val ishaPortion = p.isha / 60 * night
            if (isha.isNaN() || isha - sunset > ishaPortion) isha = sunset + ishaPortion
        }

        val midnightUtc = date.toEpochDay() * DAY_MS
        fun millis(hours: Double, offset: Int): Long {
            val minutes = floor((hours - lng / 15) * 60 + 0.5).toLong() + offset
            return midnightUtc + minutes * 60_000
        }
        val o = p.offsets
        return longArrayOf(
            millis(fajr, o[0]), millis(sunrise, o[1]), millis(dhuhr, o[2]),
            millis(asr, o[3]), millis(sunset, o[4]), millis(isha, o[5]), millis(sunset, 0),
        )
    }

    private const val RISE_SET = 0.833
    private const val DAY_MS = 86_400_000L

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** The sun's declination (degrees) and the equation of time (hours). */
    private fun sun(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = deg(atan2(dcos(e) * dsin(l), dcos(l))) / 15
        return doubleArrayOf(deg(asin(dsin(e) * dsin(l))), q / 15 - fixHour(ra))
    }

    private fun midDay(jd: Double, t: Double) = fixHour(12 - sun(jd + t)[1])

    private fun angleTime(jd: Double, lat: Double, angle: Double, t: Double, ccw: Boolean): Double {
        val decl = sun(jd + t)[0]
        val x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        if (x < -1 || x > 1) return Double.NaN
        val h = deg(acos(x)) / 15
        return midDay(jd, t) + if (ccw) -h else h
    }

    private fun asrTime(jd: Double, lat: Double, factor: Double, t: Double): Double {
        val decl = sun(jd + t)[0]
        val angle = -deg(atan(1 / (factor + dtan(abs(lat - decl)))))
        return angleTime(jd, lat, angle, t, ccw = false)
    }

    private fun deg(r: Double) = Math.toDegrees(r)
    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun fixAngle(a: Double) = ((a % 360) + 360) % 360
    private fun fixHour(h: Double) = ((h % 24) + 24) % 24
}
