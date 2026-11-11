package app.muhib

import android.content.Context
import android.content.SharedPreferences

/** Saved choices: calculation method, Asr school, which place is showing, which view. */
object Store {
    fun prefs(context: Context): SharedPreferences = Muhib.prefs(context)

    class Method(val id: Int, val name: Int)

    /** The most common methods, as AlAdhan numbers them. */
    val methods = listOf(
        Method(3, R.string.method_mwl),
        Method(13, R.string.method_diyanet),
        Method(2, R.string.method_isna),
        Method(5, R.string.method_egypt),
        Method(4, R.string.method_makkah),
        Method(1, R.string.method_karachi),
    )

    fun method(context: Context): Int =
        prefs(context).getInt("method", if (Lang.current(context) == Lang.TR) 13 else 3)

    fun setMethod(context: Context, id: Int) = prefs(context).edit().putInt("method", id).apply()

    /** 0 = standard (Shafi, Maliki, Hanbali), 1 = Hanafi. */
    fun school(context: Context): Int = prefs(context).getInt("school", 0)

    fun setSchool(context: Context, school: Int) = prefs(context).edit().putInt("school", school).apply()

    fun methodName(context: Context): String =
        context.getString(methods.firstOrNull { it.id == method(context) }?.name ?: R.string.method_mwl)

    fun tab(context: Context): Int = prefs(context).getInt("tab", 0)
    fun setTab(context: Context, tab: Int) = prefs(context).edit().putInt("tab", tab).apply()
}
