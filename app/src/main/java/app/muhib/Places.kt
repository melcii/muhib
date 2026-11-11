package app.muhib

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/**
 * A place to show times for: where you are now, or a city you added.
 * For where you are, [accuracy] (metres) and [fixedAt] describe the last location fix.
 */
data class Place(
    val id: String,
    val name: String,
    val detail: String,
    val lat: Double,
    val lng: Double,
    val tz: String = "",
    val accuracy: Float = 0f,
    val fixedAt: Long = 0,
) {
    val isHere get() = id == Places.HERE

    /** The place's own time zone (from the built-in city list), so times are right even if the phone's clock zone isn't. */
    fun zone(context: Context): ZoneId = when {
        tz.isNotEmpty() -> runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.systemDefault())
        else -> Cities.nearest(context, lat, lng)?.zone ?: ZoneId.systemDefault()
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("detail", detail)
        .put("lat", lat).put("lng", lng).put("tz", tz).put("acc", accuracy.toDouble()).put("at", fixedAt)

    companion object {
        fun from(o: JSONObject) = Place(
            o.getString("id"), o.optString("name"), o.optString("detail"), o.getDouble("lat"), o.getDouble("lng"),
            o.optString("tz"), o.optDouble("acc", 0.0).toFloat(), o.optLong("at"),
        )
    }
}

object Places {
    const val HERE = "here"
    private const val KEY_HERE = "here"
    private const val KEY_CITIES = "cities"
    private const val KEY_SELECTED = "selected"

    fun here(context: Context): Place? =
        Store.prefs(context).getString(KEY_HERE, null)?.let { runCatching { Place.from(JSONObject(it)) }.getOrNull() }

    fun setHere(context: Context, place: Place) =
        Store.prefs(context).edit().putString(KEY_HERE, place.toJson().toString()).apply()

    fun cities(context: Context): List<Place> {
        val arr = runCatching { JSONArray(Store.prefs(context).getString(KEY_CITIES, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { Place.from(arr.getJSONObject(it)) }
    }

    private fun saveCities(context: Context, list: List<Place>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        Store.prefs(context).edit().putString(KEY_CITIES, arr.toString()).apply()
    }

    fun add(context: Context, place: Place) {
        saveCities(context, cities(context).filter { it.id != place.id } + place)
    }

    fun remove(context: Context, id: String) {
        saveCities(context, cities(context).filter { it.id != id })
        if (selectedId(context) == id) select(context, HERE)
        Times.forget(id)
    }

    fun selectedId(context: Context): String = Store.prefs(context).getString(KEY_SELECTED, HERE) ?: HERE

    fun select(context: Context, id: String) = Store.prefs(context).edit().putString(KEY_SELECTED, id).apply()

    /** The place the app is showing. Null only when there is no location yet. */
    fun selected(context: Context): Place? {
        val id = selectedId(context)
        return if (id == HERE) here(context) else cities(context).firstOrNull { it.id == id } ?: here(context)
    }
}
