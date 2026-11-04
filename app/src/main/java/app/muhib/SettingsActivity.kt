package app.muhib

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Switch
import android.widget.TextView
import java.util.Calendar

/** When it renews, the quiet reminder, and the app's language. */
class SettingsActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            findViewById<View>(R.id.scroll).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.morningRow).setOnClickListener { pickTime(Muhib.KEY_MORNING, Reminders.morning(this)) }
        findViewById<View>(R.id.nightRow).setOnClickListener { pickTime(Muhib.KEY_NIGHT, Reminders.night(this)) }

        val quiet = findViewById<Switch>(R.id.quiet)
        quiet.isChecked = Reminders.quietOn(this)
        quiet.setOnCheckedChangeListener { _, on ->
            Muhib.prefs(this).edit().putBoolean(Muhib.KEY_QUIET_REMINDERS, on).apply()
            if (on) askForNotifications()
        }
        findViewById<View>(R.id.quietRow).setOnClickListener { quiet.toggle() }

        findViewById<View>(R.id.langEn).setOnClickListener { switchLanguage(Lang.EN) }
        findViewById<View>(R.id.langTr).setOnClickListener { switchLanguage(Lang.TR) }

        render()
        if (savedInstanceState == null) Motion.stagger(Motion.children(findViewById<ViewGroup>(R.id.page)), step = 70)
    }

    private fun render() {
        findViewById<TextView>(R.id.morningTime).text = formatTime(Reminders.morning(this))
        findViewById<TextView>(R.id.nightTime).text = formatTime(Reminders.night(this))
        val lang = Lang.current(this)
        listOf(R.id.langEn to Lang.EN, R.id.langTr to Lang.TR).forEach { (id, code) ->
            findViewById<TextView>(id).apply {
                isSelected = lang == code
                setTextColor(getColor(if (lang == code) R.color.ink else R.color.muted))
            }
        }
    }

    private fun switchLanguage(lang: String) {
        if (lang == Lang.current(this)) return
        Lang.set(this, lang)
        MuhibWidget.refreshAll(applicationContext)
        recreate()
    }

    private fun pickTime(key: String, minutes: Int) {
        TimePickerDialog(this, { _, h, m ->
            Muhib.prefs(this).edit().putInt(key, h * 60 + m).apply()
            Reminders.schedule(this)
            render()
        }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(this)).show()
    }

    private fun formatTime(minutes: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        return DateFormat.getTimeFormat(this).format(cal.time)
    }

    private fun askForNotifications() {
        Muhib.prefs(this).edit().putBoolean(MainActivity.KEY_ASKED, true).apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
