package app.muhib

import android.content.Context
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale
import kotlin.math.cos

/**
 * About 65,000 towns and cities (GeoNames, population over 5,000) stored in the app, for naming
 * where you are and for city search, without asking any online service.
 */
object Cities {
    class City(
        val id: Long,
        val name: String,
        val lat: Double,
        val lng: Double,
        val country: String,
        val tz: String,
        val population: Long,
        val key: String,
        val asciiKey: String,
    ) {
        val zone: ZoneId get() = runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.systemDefault())
    }

    @Volatile
    private var all: List<City>? = null

    /** Reads the list once (a few hundred ms); call off the main thread when you can. */
    @Synchronized
    fun load(context: Context): List<City> = all ?: context.assets.open("cities.tsv").bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val c = line.split('\t')
            if (c.size < 8) return@mapNotNull null
            City(
                c[0].toLong(), c[1], c[3].toDouble(), c[4].toDouble(), c[5], c[6], c[7].toLongOrNull() ?: 0,
                normalize(c[1]), normalize(c[2]),
            )
        }.toList()
    }.also { all = it }

    /**
     * The town you're in: the nearest one, unless a big city (a million or more) is close by,
     * so a district of Istanbul still reads "Istanbul".
     */
    fun nearest(context: Context, lat: Double, lng: Double): City? {
        val k = cos(Math.toRadians(lat))
        fun d2(c: City): Double {
            val dx = (c.lng - lng) * k
            val dy = c.lat - lat
            return dx * dx + dy * dy
        }
        val cities = load(context)
        val big = cities.filter { it.population >= 1_000_000 && d2(it) < BIG_RADIUS * BIG_RADIUS }.maxByOrNull { it.population }
        return big ?: cities.minByOrNull(::d2)
    }

    /** Names starting with the text first, then names containing it; bigger places first. */
    fun search(context: Context, text: String, limit: Int = 20): List<City> {
        val q = normalize(text)
        if (q.isEmpty()) return emptyList()
        val cities = load(context)
        val starts = cities.filter { it.key.startsWith(q) || it.asciiKey.startsWith(q) }.sortedByDescending { it.population }
        if (starts.size >= limit) return starts.take(limit)
        val contains = cities.filter { (q in it.key || q in it.asciiKey) && it !in starts }.sortedByDescending { it.population }
        return (starts + contains).take(limit)
    }

    fun place(context: Context, city: City) =
        Place("city-${city.id}", city.name, country(context, city.country), city.lat, city.lng, city.tz)

    fun country(context: Context, code: String): String =
        Locale("", code).getDisplayCountry(Locale(Lang.current(context))).ifEmpty { code }

    /** Lower-case without accents, so "istanbul" finds "İstanbul" and "koln" finds "Köln". */
    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD)
        .replace(MARKS, "").trim()

    private val MARKS = Regex("\\p{Mn}+")
    private const val BIG_RADIUS = 0.3 // degrees, about 30 km
}
