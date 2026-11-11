package app.muhib

import java.time.Instant

/** Everything the screens and widget need to know about "now" for one place. */
class Moment(val schedule: Schedule, val now: Long = System.currentTimeMillis()) {
    val today = Instant.ofEpochMilli(now).atZone(schedule.zone).toLocalDate()
    val day: Day? = schedule.day(today)
    private val tomorrow = schedule.day(today.plusDays(1))
    private val yesterday = schedule.day(today.minusDays(1))

    val ready get() = day != null

    /** 0..5 for the next time today, or 6 when the next one is tomorrow's imsak. */
    val nextIndex: Int = day?.t?.indexOfFirst { it > now }?.let { if (it < 0) 6 else it } ?: 0

    /** Which of the six names comes next. */
    val next get() = nextIndex % 6

    val nextAt: Long
        get() = if (nextIndex < 6) day!!.t[nextIndex] else tomorrow?.imsak ?: (day!!.imsak + DAY)

    /** The time that came before the next one, for progress lines. */
    val previousAt: Long
        get() = if (nextIndex == 0) yesterday?.t?.get(Day.ISHA) ?: (day!!.t[Day.ISHA] - DAY) else day!!.t[nextIndex - 1]

    val previous get() = (nextIndex + 5) % 6

    /** The time we are in now (before imsak, that is still last night's Isha). */
    val current get() = previous

    /** The night runs from Maghrib to the next Fajr (imsak). */
    val nightStart: Long
        get() = if (now < day!!.imsak) yesterday?.maghrib ?: (day.maghrib - DAY) else day.maghrib

    val nightEnd: Long
        get() = if (now < day!!.imsak) day.imsak else tomorrow?.imsak ?: (day.imsak + DAY)

    val inNight get() = now in nightStart until nightEnd

    val nightProgress get() = ((now - nightStart).toFloat() / (nightEnd - nightStart)).coerceIn(0f, 1f)

    fun nightMark(fraction: Double) = nightStart + ((nightEnd - nightStart) * fraction).toLong()

    /** The Islamic day begins at Maghrib, so after sunset this is tomorrow's date. */
    val hijri: Day? get() = if (day != null && now >= day.maghrib) tomorrow ?: day else day

    /**
     * The three times voluntary prayer is avoided, as [start, end] pairs: while the sun rises,
     * when it stands at its zenith just before Dhuhr, and while it sets.
     */
    val forbidden: List<Pair<Long, Long>>
        get() {
            val d = day!!
            return listOf(
                d.sunrise to d.sunrise + 20 * MIN,
                d.dhuhr - 10 * MIN to d.dhuhr,
                d.sunset - 20 * MIN to d.sunset,
            )
        }

    companion object {
        const val MIN = 60_000L
        const val DAY = 24 * 60 * MIN
    }
}
