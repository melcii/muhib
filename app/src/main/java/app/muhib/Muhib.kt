package app.muhib

import android.content.Context
import android.content.SharedPreferences

/**
 * The single piece of state shared by the widget and the app:
 * which reminder is waiting, and whether it has been revealed yet.
 */
object Muhib {
    private const val PREFS = "muhib"
    private const val KEY_CURRENT = "current"
    private const val KEY_REVEALED = "revealed"
    private const val KEY_BAG = "bag"
    private const val KEY_HISTORY = "history"
    const val KEY_MORNING = "morning_minutes"
    const val KEY_NIGHT = "night_minutes"
    const val KEY_QUIET_REMINDERS = "quiet_reminders"
    const val KEY_NEXT_NOTIFY = "next_notify_at"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(context: Context): Reminder =
        Content.byId(context, prefs(context).getString(KEY_CURRENT, null)) ?: coverAgain(context)

    fun isRevealed(context: Context): Boolean = prefs(context).getBoolean(KEY_REVEALED, false)

    fun reveal(context: Context): Reminder {
        val reminder = current(context)
        val p = prefs(context)
        if (!p.getBoolean(KEY_REVEALED, false)) {
            val history = listOf(reminder.id) + history(p).filter { it != reminder.id }
            p.edit()
                .putBoolean(KEY_REVEALED, true)
                .putString(KEY_HISTORY, history.take(100).joinToString(","))
                .apply()
        }
        return reminder
    }

    /** Blur the widget again with a fresh pick. */
    fun coverAgain(context: Context): Reminder {
        val next = drawNext(context)
        prefs(context).edit().putString(KEY_CURRENT, next.id).putBoolean(KEY_REVEALED, false).apply()
        return next
    }

    /** Shuffle-bag draw: every reminder appears once before any repeats. */
    private fun drawNext(context: Context): Reminder {
        val all = Content.all(context)
        val ids = all.map { it.id }.toSet()
        val p = prefs(context)
        var bag = p.getString(KEY_BAG, "").orEmpty().split(",").filter { it in ids }.toMutableList()
        if (bag.isEmpty()) {
            bag = all.map { it.id }.shuffled().toMutableList()
            val last = p.getString(KEY_CURRENT, null)
            if (bag.size > 1 && bag.first() == last) bag.add(bag.removeAt(0))
        }
        val id = bag.removeAt(0)
        p.edit().putString(KEY_BAG, bag.joinToString(",")).apply()
        return all.first { it.id == id }
    }

    private fun history(p: SharedPreferences): List<String> =
        p.getString(KEY_HISTORY, "").orEmpty().split(",").filter { it.isNotEmpty() }
}
