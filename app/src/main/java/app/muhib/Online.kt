package app.muhib

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prayer times downloaded from AlAdhan, only when the user taps "Update prayer times".
 * Each place's coordinates are rounded to about 1 km before they are sent, and nothing else about
 * the user goes with them. Saved times are used only while the place, method, Asr choice and time
 * zone still match; any other day is calculated on the phone.
 */
object Online {
    const val DAYS = 61
    private const val FILE = "aladhan.json"

    class Coverage(val at: Long, val until: LocalDate)

    private var cache: JSONObject? = null

    /** The coordinates as sent: two decimals, about a kilometre. */
    private fun round(v: Double) = String.format(Locale.US, "%.2f", v)

    private fun key(place: Place, zone: ZoneId, method: Int, school: Int) =
        "${round(place.lat)},${round(place.lng)}|$method|$school|$zone"

    @Synchronized
    private fun all(context: Context): JSONObject = cache ?: runCatching {
        JSONObject(File(context.filesDir, FILE).readText())
    }.getOrElse { JSONObject() }.also { cache = it }

    @Synchronized
    private fun save(context: Context, data: JSONObject) {
        cache = data
        File(context.filesDir, FILE).writeText(data.toString())
    }

    /** Downloaded days for this place, if they were made with the current settings. */
    fun days(context: Context, place: Place, zone: ZoneId, method: Int, school: Int): Map<LocalDate, LongArray> {
        val entry = all(context).optJSONObject(place.id) ?: return emptyMap()
        if (entry.optString("key") != key(place, zone, method, school)) return emptyMap()
        val days = entry.optJSONObject("days") ?: return emptyMap()
        val out = HashMap<LocalDate, LongArray>()
        days.keys().forEach { d ->
            val arr = days.getJSONArray(d)
            out[LocalDate.parse(d)] = LongArray(arr.length()) { arr.getLong(it) }
        }
        return out
    }

    /** When the place was last updated with the current settings, and the last day it covers. */
    fun coverage(context: Context, place: Place): Coverage? {
        val zone = place.zone(context)
        val entry = all(context).optJSONObject(place.id) ?: return null
        if (entry.optString("key") != key(place, zone, Store.method(context), Store.school(context))) return null
        val last = entry.optJSONObject("days")?.keys()?.asSequence()?.maxOrNull() ?: return null
        return Coverage(entry.optLong("at"), LocalDate.parse(last))
    }

    /**
     * Downloads the next [DAYS] days for every saved place. Blocking: call it off the main thread.
     * Returns how many places were updated; throws if none could be.
     */
    fun update(context: Context): Int {
        val method = Store.method(context)
        val school = Store.school(context)
        val places = listOfNotNull(Places.here(context)) + Places.cities(context)
        val data = all(context).let { JSONObject(it.toString()) }
        val keep = places.map { it.id }.toSet()
        data.keys().asSequence().toList().filter { it !in keep }.forEach { data.remove(it) }
        var updated = 0
        var error: Exception? = null
        for (place in places) {
            try {
                val zone = place.zone(context)
                val today = LocalDate.now(zone)
                val last = today.plusDays(DAYS.toLong() - 1)
                val days = JSONObject()
                var month = YearMonth.from(today)
                while (!month.atDay(1).isAfter(last)) {
                    fetchMonth(place, zone, method, school, month).forEach { (date, t) ->
                        if (!date.isBefore(today) && !date.isAfter(last)) days.put(date.toString(), JSONArray(t.toList()))
                    }
                    month = month.plusMonths(1)
                }
                data.put(place.id, JSONObject().put("key", key(place, zone, method, school))
                    .put("at", System.currentTimeMillis()).put("days", days))
                updated++
            } catch (e: Exception) {
                error = e
            }
        }
        save(context, data)
        Times.forgetAll()
        if (updated == 0 && error != null) throw error
        return updated
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val names = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha", "Sunset")

    /** One month from AlAdhan's calendar, as Fajr..Isha plus sunset in epoch ms. */
    private fun fetchMonth(place: Place, zone: ZoneId, method: Int, school: Int, month: YearMonth): List<Pair<LocalDate, LongArray>> {
        val url = "https://api.aladhan.com/v1/calendar/${month.year}/${month.monthValue}" +
            "?latitude=${round(place.lat)}&longitude=${round(place.lng)}&method=$method&school=$school" +
            "&timezonestring=${zone.id}"
        val list = get(url).getJSONArray("data")
        return (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            val date = LocalDate.parse(o.getJSONObject("date").getJSONObject("gregorian").getString("date"), dateFormat)
            val timings = o.getJSONObject("timings")
            var previous = Long.MIN_VALUE
            val t = LongArray(7) { k ->
                // "05:34 (+03)": the clock time comes first. A time past midnight belongs to the next day.
                val clock = LocalTime.parse(timings.getString(names[k]).take(5))
                var ms = date.atTime(clock).atZone(zone).toInstant().toEpochMilli()
                if (k < 6 && ms < previous) ms += Moment.DAY
                if (k < 6) previous = ms
                ms
            }
            date to t
        }
    }

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
