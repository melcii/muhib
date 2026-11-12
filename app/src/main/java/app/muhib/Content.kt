package app.muhib

import android.content.Context
import org.json.JSONObject

data class Reminder(
    val id: String,
    val type: String,
    val arabic: String?,
    val text: String,
    val source: String,
    val more: String,
    val link: String?,
)

/**
 * Loads the reminders from assets/content.json, which tools/content.py builds from the
 * reminders spreadsheet. Each item may carry a block per language ("tr", ...) with its
 * text/source/more; anything missing falls back to English.
 */
object Content {
    @Volatile private var cache: Pair<String, List<Reminder>>? = null

    fun all(context: Context): List<Reminder> {
        val lang = Lang.current(context)
        cache?.let { if (it.first == lang) return it.second }
        return synchronized(this) { load(context, lang).also { cache = lang to it } }
    }

    fun byId(context: Context, id: String?): Reminder? = all(context).firstOrNull { it.id == id }

    private fun load(context: Context, lang: String): List<Reminder> {
        val json = context.assets.open("content.json").bufferedReader().use { it.readText() }
        val items = JSONObject(json).getJSONArray("items")
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            // English is the item itself; any other language is a block named by its code ("tr", "ar", ...).
            val local = if (lang == Lang.EN) o else o.optJSONObject(lang) ?: o
            Reminder(
                id = o.getString("id"),
                type = o.getString("type"),
                arabic = o.optString("arabic").ifEmpty { null },
                text = local.optString("text").ifEmpty { o.getString("text") },
                source = local.optString("source").ifEmpty { o.getString("source") },
                more = local.optString("more").ifEmpty { o.optString("more") },
                link = o.optString("link").ifEmpty { null },
            )
        }
    }
}
