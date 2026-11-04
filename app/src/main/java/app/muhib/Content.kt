package app.muhib

import android.content.Context
import org.json.JSONObject

data class Reminder(
    val id: String,
    val type: String,
    val headline: String,
    val arabic: String?,
    val text: String,
    val source: String,
    val more: String,
    val link: String?,
)

/**
 * Loads the reminders from assets/content.json. Swap that file to change what Muhib shows.
 * Each item may carry a "tr" object with Turkish headline/text/source/more.
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
            val local = if (lang == Lang.TR) o.optJSONObject("tr") ?: o else o
            val type = o.getString("type")
            Reminder(
                id = o.getString("id"),
                type = type,
                headline = local.optString("headline").ifEmpty { defaultHeadline(type, lang) },
                arabic = o.optString("arabic").ifEmpty { null },
                text = local.optString("text").ifEmpty { o.getString("text") },
                source = local.optString("source").ifEmpty { o.getString("source") },
                more = local.optString("more").ifEmpty { o.optString("more") },
                link = o.optString("link").ifEmpty { null },
            )
        }
    }

    private fun defaultHeadline(type: String, lang: String) =
        if (lang == Lang.TR) {
            val name = when (type) {
                "ayah" -> "ayet"
                "hadith" -> "hadis"
                "sunnah" -> "sünnet"
                "chapter" -> "bölüm"
                "dua" -> "dua"
                else -> type
            }
            "Bu $name tam ihtiyacın olduğu anda geldi"
        } else {
            "This ${if (type == "dua") "du'a" else type} came when you needed it the most"
        }
}
