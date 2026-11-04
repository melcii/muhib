package app.muhib

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** The app's own language choice (English or Turkish), independent of the phone's language. */
object Lang {
    const val EN = "en"
    const val TR = "tr"
    private const val KEY = "language"

    /** Follows the phone (Turkish if the phone is Turkish) until the user picks one. */
    fun current(context: Context): String =
        Muhib.prefs(context).getString(KEY, null)
            ?: if (Resources.getSystem().configuration.locales[0].language == TR) TR else EN

    fun set(context: Context, lang: String) {
        Muhib.prefs(context).edit().putString(KEY, lang).apply()
    }

    /** A context whose resources speak the chosen language. */
    fun wrap(context: Context): Context {
        val locale = Locale(current(context))
        // Text views upper-case labels with the default locale, and Turkish needs its own dotted İ.
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
