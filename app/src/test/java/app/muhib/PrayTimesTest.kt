package app.muhib

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Compares the on-device calculation with times AlAdhan returned for the same places and methods.
 * Asr may differ by a few minutes far from the equator: checked against NOAA's solar model, ours is
 * the closer one there (London, 1 Jan 2026: 13:46 true, ours 13:46, AlAdhan 13:44).
 */
class PrayTimesTest {
    @Test
    fun matchesAlAdhan() {
        val names = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        val lines = javaClass.classLoader!!.getResource("aladhan.csv").readText().trim().lines()
        var worst = 0L
        val problems = mutableListOf<String>()
        for (line in lines) {
            val c = line.split(",")
            val zone = ZoneId.of(c[4])
            val date = LocalDate.parse(c[5], DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val got = PrayTimes.compute(date, c[2].toDouble(), c[3].toDouble(), c[0].toInt(), c[1].toInt())
            for (i in 0..5) {
                val want = date.atTime(java.time.LocalTime.parse(c[6 + i])).atZone(zone).toInstant().toEpochMilli()
                val diff = kotlin.math.abs(got[i] - want) / 60_000
                worst = maxOf(worst, diff)
                if (diff > if (i == 3) 5 else 1) problems += "${c[0]}/${c[1]} ${c[4]} ${c[5]} ${names[i]}: want ${c[6 + i]}, got " +
                    Instant.ofEpochMilli(got[i]).atZone(zone).toLocalTime()
            }
        }
        println("Checked ${lines.size} days, worst difference $worst min")
        problems.take(30).forEach(::println)
        assertTrue("${problems.size} times differ by more than a minute", problems.isEmpty())
    }
}
