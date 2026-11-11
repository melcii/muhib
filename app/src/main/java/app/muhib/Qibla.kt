package app.muhib

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Qibla {
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206

    /** Degrees clockwise from true north, along the great circle to the Kaaba. */
    fun bearing(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun distanceToKaaba(lat: Double, lng: Double) = distanceKm(lat, lng, KAABA_LAT, KAABA_LNG)

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dl / 2).let { it * it }
        return 2 * 6371.0 * asin(sqrt(a))
    }
}
