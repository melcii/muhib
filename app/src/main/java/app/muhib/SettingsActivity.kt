package app.muhib

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.util.Calendar

/** Opens the system page where the user allows exact alarms for this app. */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
    }
}

/**
 * Two sides, Prayer and Muhib, each with its own settings; under them, what both share:
 * colour, widgets and language. The shared ones are single settings, so changing them on
 * either side changes both.
 */
class SettingsActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Look.wrap(Lang.wrap(newBase)))
    }

    private var side = PRAYER
    private var createdKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.apply(this)
        setContentView(R.layout.activity_settings)
        Accent.paint(window.decorView)
        Look.window(this)
        createdKey = Accent.key(this)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            findViewById<View>(R.id.scroll).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        side = savedInstanceState?.getInt(KEY_SIDE) ?: intent.getIntExtra(EXTRA_SIDE, Store.prefs(this).getInt(KEY_SIDE, PRAYER))
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.sidePrayer).setOnClickListener { showSide(PRAYER, animate = true) }
        findViewById<View>(R.id.sideMuhib).setOnClickListener { showSide(MUHIB, animate = true) }

        // Prayer side.
        findViewById<View>(R.id.asrStandard).setOnClickListener { setSchool(0) }
        findViewById<View>(R.id.asrHanafi).setOnClickListener { setSchool(1) }
        findViewById<View>(R.id.updateRow).setOnClickListener { update() }
        findViewById<View>(R.id.notifyRow).setOnClickListener {
            when {
                PrayerAlerts.on(this) && !PrayerAlerts.allowed(this) -> askForNotifications()
                else -> {
                    PrayerAlerts.setOn(this, !PrayerAlerts.on(this))
                    if (PrayerAlerts.on(this)) askForNotifications()
                    notifyChanged()
                }
            }
        }
        findViewById<View>(R.id.atRow).setOnClickListener { PrayerAlerts.setAtTime(this, !PrayerAlerts.atTime(this)); notifyChanged() }
        findViewById<View>(R.id.beforeRow).setOnClickListener { pickBefore() }
        findViewById<View>(R.id.whichRow).setOnClickListener { pickWhich() }

        // Muhib side.
        findViewById<View>(R.id.renewSun).setOnClickListener { setRenew(true) }
        findViewById<View>(R.id.renewSet).setOnClickListener { setRenew(false) }
        findViewById<View>(R.id.morningRow).setOnClickListener { pickTime(Muhib.KEY_MORNING, Reminders.morning(this)) }
        findViewById<View>(R.id.nightRow).setOnClickListener { pickTime(Muhib.KEY_NIGHT, Reminders.night(this)) }
        findViewById<View>(R.id.quietRow).setOnClickListener {
            val on = !Reminders.quietOn(this)
            Muhib.prefs(this).edit().putBoolean(Muhib.KEY_QUIET_REMINDERS, on).apply()
            if (on) askForNotifications()
            renderMuhib()
        }

        // Shared.
        findViewById<View>(R.id.matchRow).setOnClickListener {
            Look.setOn(this, !Look.on(this))
            recreate()
        }
        findViewById<View>(R.id.fontRow).setOnClickListener {
            FontPicker.show(this) { font ->
                Fonts.set(this, font)
                Widgets.refreshAll(applicationContext)
                recreate()
            }
        }
        findViewById<View>(R.id.styleShared).setOnClickListener { setShared(true) }
        findViewById<View>(R.id.styleEach).setOnClickListener { setShared(false) }
        findViewById<View>(R.id.addWidgetRow).apply {
            visibility = if (Widgets.pinSupported(context)) View.VISIBLE else View.GONE
            setOnClickListener { WidgetPicker.show(this@SettingsActivity) }
        }
        // Android 12+ asks the user before an app may set exact alarms; widgets and notifications use them.
        findViewById<View>(R.id.exactRow).apply {
            visibility = if (Build.VERSION.SDK_INT >= 31) View.VISIBLE else View.GONE
            setOnClickListener { openExactAlarmSettings(this@SettingsActivity) }
        }
        findViewById<View>(R.id.addWidgetDivider).visibility =
            if (Build.VERSION.SDK_INT >= 31 && Widgets.pinSupported(this)) View.VISIBLE else View.GONE
        findViewById<View>(R.id.langEn).setOnClickListener { switchLanguage(Lang.EN) }
        findViewById<View>(R.id.langTr).setOnClickListener { switchLanguage(Lang.TR) }
        findViewById<TextView>(R.id.credits).text = getString(R.string.credits) + "\n\n" + getString(R.string.prayer_credits)

        showSide(side, animate = false)
        render()
        if (savedInstanceState == null) Motion.stagger(Motion.children(findViewById<ViewGroup>(R.id.page)).take(6), step = 60)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SIDE, side)
    }

    override fun onResume() {
        super.onResume()
        // Back from styling a widget the app follows: redraw in its colours.
        if (Accent.key(this) != createdKey) return recreate()
        val exact = Widgets.exact(this)
        findViewById<TextView>(R.id.exactState).apply {
            setText(if (exact) R.string.exact_on else R.string.exact_off)
            setTextColor(if (exact) getColor(R.color.muted) else Accent.color(this@SettingsActivity))
        }
        Widgets.refreshAll(applicationContext)
        renderNotify()
        renderWidgets()
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun render() {
        renderMethods()
        segment(listOf(R.id.asrStandard to 0, R.id.asrHanafi to 1), Store.school(this))
        renderUpdate()
        renderNotify()
        renderMuhib()
        renderAccent()
        renderWidgets()
        segment(listOf(R.id.langEn to Lang.EN, R.id.langTr to Lang.TR), Lang.current(this))
    }

    // ---- sides ----

    /** The chosen side slides in from where its button is; the other slides out. */
    private fun showSide(next: Int, animate: Boolean) {
        val old = side
        side = next
        Store.prefs(this).edit().putInt(KEY_SIDE, next).apply()
        segment(listOf(R.id.sidePrayer to PRAYER, R.id.sideMuhib to MUHIB), next)
        val views = listOf<View>(findViewById(R.id.prayerSide), findViewById(R.id.muhibSide))
        val shown = views[next]
        val hidden = views[1 - next]
        if (!animate || old == next) {
            shown.visibility = View.VISIBLE
            hidden.visibility = View.GONE
            return
        }
        val dir = if (next > old) 1 else -1
        val shift = 48.dp.toFloat()
        hidden.animate().alpha(0f).translationX(-dir * shift).setDuration(160).setInterpolator(Motion.ease).withEndAction {
            hidden.visibility = View.GONE
            hidden.translationX = 0f
            hidden.alpha = 1f
            shown.visibility = View.VISIBLE
            shown.alpha = 0f
            shown.translationX = dir * shift
            shown.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(Motion.ease).start()
        }.start()
    }

    // ---- prayer side ----

    private fun renderMethods() {
        val group = findViewById<LinearLayout>(R.id.methods)
        group.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val current = Store.method(this)
        Store.methods.forEachIndexed { i, method ->
            if (i > 0) group.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.hairline))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply { marginStart = 20.dp }
            })
            val row = inflater.inflate(R.layout.row_choice, group, false)
            Accent.paint(row)
            row.findViewById<TextView>(R.id.name).text = getString(method.name)
            row.findViewById<View>(R.id.check).visibility = if (method.id == current) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener {
                if (method.id == current) return@setOnClickListener
                Store.setMethod(this, method.id)
                prayerChanged()
            }
            group.addView(row)
        }
    }

    private fun renderNotify() {
        val on = PrayerAlerts.on(this)
        val blocked = on && !PrayerAlerts.allowed(this)
        findViewById<Switch>(R.id.notifySwitch).isChecked = on && !blocked
        findViewById<TextView>(R.id.notifySub).apply {
            setText(if (blocked) R.string.notify_blocked else R.string.notify_sub)
            setTextColor(if (blocked) Accent.color(this@SettingsActivity) else getColor(R.color.muted))
        }
        val before = PrayerAlerts.before(this)
        findViewById<TextView>(R.id.beforeValue).text =
            if (before == 0) getString(R.string.notify_off) else getString(R.string.notify_minutes, before)
        findViewById<Switch>(R.id.atSwitch).isChecked = PrayerAlerts.atTime(this)
        val names = resources.getStringArray(R.array.prayers)
        val which = PrayerAlerts.which(this)
        findViewById<TextView>(R.id.whichSub).text =
            names.filterIndexed { i, _ -> which and (1 shl i) != 0 }.joinToString(", ").ifEmpty { getString(R.string.notify_none) }
        listOf(R.id.beforeRow, R.id.atRow, R.id.whichRow).forEach {
            findViewById<View>(it).apply { isEnabled = on; animate().alpha(if (on) 1f else 0.4f).setDuration(200).start() }
        }
    }

    private fun notifyChanged() {
        renderNotify()
        PrayerAlerts.schedule(this)
    }

    private fun askForNotifications() {
        if (PrayerAlerts.allowed(this)) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) || !Store.prefs(this).getBoolean(MainActivity.KEY_ASKED, false)) {
            Store.prefs(this).edit().putBoolean(MainActivity.KEY_ASKED, true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            // Android won't ask again: send them to the app's notification settings.
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        notifyChanged()
        renderMuhib()
    }

    private fun pickBefore() {
        val choices = PrayerAlerts.beforeChoices
        val labels = choices.map { if (it == 0) getString(R.string.notify_off) else getString(R.string.notify_minutes, it) }.toTypedArray()
        Accent.show(AlertDialog.Builder(this)
            .setTitle(R.string.notify_before)
            .setSingleChoiceItems(labels, choices.indexOf(PrayerAlerts.before(this))) { d, i ->
                PrayerAlerts.setBefore(this, choices[i])
                notifyChanged()
                d.dismiss()
            })
    }

    private fun pickWhich() {
        val names = resources.getStringArray(R.array.prayers)
        var bits = PrayerAlerts.which(this)
        Accent.show(AlertDialog.Builder(this)
            .setTitle(R.string.notify_which)
            .setMultiChoiceItems(names, BooleanArray(6) { bits and (1 shl it) != 0 }) { _, i, on ->
                bits = if (on) bits or (1 shl i) else bits and (1 shl i).inv()
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                PrayerAlerts.setWhich(this, bits)
                notifyChanged()
            })
    }

    private var updating = false
    private var updateFailed = false

    /** The update row: when the shown place was last downloaded, or why it couldn't be. */
    private fun renderUpdate() {
        val sub = findViewById<TextView>(R.id.updateSub)
        val place = Places.selected(this)
        val coverage = place?.let { Online.coverage(this, it) }
        sub.text = when {
            updateFailed -> getString(R.string.update_failed)
            place == null -> getString(R.string.update_none)
            coverage != null -> getString(R.string.update_done, Fmt.ago(this, coverage.at),
                java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale(Lang.current(this))).format(coverage.until))
            else -> getString(R.string.update_never)
        }
        sub.setTextColor(getColor(if (updateFailed) R.color.ink else R.color.muted))
        findViewById<TextView>(R.id.updateState).setText(if (updating) R.string.update_running else R.string.update_action)
        findViewById<View>(R.id.updateRow).isEnabled = !updating && place != null
    }

    private fun update() {
        if (updating) return
        updating = true
        updateFailed = false
        renderUpdate()
        Refresh.run {
            val ok = runCatching { Online.update(applicationContext) }.isSuccess
            Refresh.post {
                updating = false
                updateFailed = !ok
                Widgets.refreshAll(applicationContext)
                PrayerAlerts.schedule(applicationContext)
                Reminders.schedule(applicationContext)
                if (!isDestroyed) renderUpdate()
            }
        }
    }

    private fun setSchool(school: Int) {
        if (school == Store.school(this)) return
        Store.setSchool(this, school)
        prayerChanged()
    }

    private fun prayerChanged() {
        render()
        Widgets.refreshAll(applicationContext)
        PrayerAlerts.schedule(this)
        Reminders.schedule(this)
    }

    // ---- muhib side ----

    private fun renderMuhib() {
        val sun = Reminders.withSun(this)
        segment(listOf(R.id.renewSun to true, R.id.renewSet to false), sun)
        findViewById<TextView>(R.id.renewNote).setText(if (sun) R.string.renew_sun_note else R.string.renew_set_note)
        findViewById<View>(R.id.setTimes).visibility = if (sun) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.morningTime).text = formatTime(Reminders.morning(this))
        findViewById<TextView>(R.id.nightTime).text = formatTime(Reminders.night(this))
        findViewById<Switch>(R.id.quiet).isChecked = Reminders.quietOn(this) && PrayerAlerts.allowed(this)
    }

    private fun setRenew(sun: Boolean) {
        if (sun == Reminders.withSun(this)) return
        Reminders.setWithSun(this, sun)
        Reminders.schedule(this)
        renderMuhib()
        if (!sun) Motion.settle(findViewById(R.id.setTimes))
    }

    private fun pickTime(key: String, minutes: Int) {
        TimePickerDialog(this, { _, h, m ->
            Muhib.prefs(this).edit().putInt(key, h * 60 + m).apply()
            Reminders.schedule(this)
            renderMuhib()
        }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(this)).show()
    }

    private fun formatTime(minutes: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        return DateFormat.getTimeFormat(this).format(cal.time)
    }

    // ---- colour ----

    /** The presets and a colour wheel for your own; the chosen one is ringed. */
    private fun renderAccent() {
        val row = findViewById<LinearLayout>(R.id.accentSwatches)
        row.removeAllViews()
        val choice = Accent.choice(this)
        val night = Accent.night(this)
        Accent.presets.forEachIndexed { i, p ->
            row.addView(swatchView(if (night) p.dark else p.light, choice == i, getString(p.name)) { setAccent(i) })
        }
        val custom = Accent.custom(this)
        val shown = if (night) Accent.forDark(custom) else Accent.forLight(custom)
        row.addView(swatchView(if (choice == Accent.CUSTOM) shown else null, choice == Accent.CUSTOM, getString(R.string.accent_custom)) {
            ColorPicker.show(this, R.string.accent_pick, custom, { if (night) Accent.forDark(it) else Accent.forLight(it) }) {
                setAccent(Accent.CUSTOM, it)
            }
        })
        findViewById<Switch>(R.id.matchSwitch).isChecked = Look.on(this)
        findViewById<TextView>(R.id.matchSub).setText(when {
            !Look.on(this) -> R.string.match_off
            Look.style(this) == null -> R.string.match_auto
            else -> R.string.match_on
        })
        findViewById<TextView>(R.id.fontName).setText(Fonts.all[Fonts.choice(this)].name)
        findViewById<TextView>(R.id.accentName).text =
            Accent.presets.getOrNull(choice)?.let { getString(it.name) } ?: getString(R.string.accent_custom)
    }

    private fun swatchView(color: Int?, selected: Boolean, name: String, onClick: () -> Unit) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 40.dp, 1f).apply { marginEnd = 8.dp }
        background = ColorPicker.swatch(context, color, selected)
        scaleX = if (selected) 1f else 0.86f
        scaleY = scaleX
        contentDescription = name
        setOnClickListener { onClick() }
    }

    private fun setAccent(choice: Int, custom: Int? = null) {
        if (choice == Accent.choice(this) && custom == null) return
        Accent.set(this, choice, custom)
        Widgets.refreshAll(applicationContext)
        recreate()
    }

    // ---- widgets ----

    private fun setShared(shared: Boolean) {
        if (shared == WidgetStyle.shared(this)) return
        WidgetStyle.setShared(this, shared)
        Widgets.refreshAll(applicationContext)
        // The app may follow a different widget's colours now.
        if (Accent.key(this) != createdKey) return recreate()
        renderWidgets()
        Motion.stagger(Motion.children(findViewById(R.id.widgetList)), step = 40, rise = 6f)
    }

    /**
     * With one shared style, a single row edits it. With each its own, every placed widget
     * gets a row; with none placed yet, a short note says so.
     */
    private fun renderWidgets() {
        val shared = WidgetStyle.shared(this)
        segment(listOf(R.id.styleShared to true, R.id.styleEach to false), shared)
        findViewById<TextView>(R.id.styleNote).setText(if (shared) R.string.widgets_shared_note else R.string.widgets_each_note)
        val list = findViewById<LinearLayout>(R.id.widgetList)
        list.removeAllViews()
        val placed = Widgets.placed(this)
        val entries: List<Pair<String, Int>> = if (shared) listOf(getString(R.string.widget_style) to WidgetStyle.NONE)
        else placed.groupBy { it.first }.flatMap { (kind, ids) ->
            ids.mapIndexed { i, (_, id) ->
                (getString(Widgets.name(kind)) + if (ids.size > 1) " ${i + 1}" else "") to id
            }
        }
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                setText(R.string.widgets_none)
                textSize = 13f
                setTextColor(getColor(R.color.muted))
                setPadding(20.dp, 14.dp, 20.dp, 14.dp)
            })
            return
        }
        entries.forEachIndexed { i, (name, id) ->
            if (i > 0) list.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.hairline))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply { marginStart = 58.dp }
            })
            val style = WidgetStyle.load(this, id)
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = 56.dp
                setPadding(20.dp, 0, 20.dp, 0)
                background = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).let {
                    it.getDrawable(0).also { _ -> it.recycle() }
                }
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_brush)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(22.dp, 22.dp).apply { marginEnd = 16.dp })
                addView(TextView(context).apply {
                    text = name
                    setTextAppearance(R.style.Muhib_RowText)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                // A small swatch of the style's background.
                addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(style.background(context))
                        alpha = (60 + 195 * style.opacity / 100)
                        setStroke(1.dp, getColor(R.color.hairline))
                    }
                }, LinearLayout.LayoutParams(22.dp, 22.dp))
                setOnClickListener {
                    startActivity(Intent(context, WidgetStyleActivity::class.java).putExtra(WidgetStyleActivity.EXTRA_EDIT, id))
                }
            })
        }
    }

    // ---- language ----

    private fun <T> segment(items: List<Pair<Int, T>>, value: T) = items.forEach { (id, v) ->
        findViewById<TextView>(id).apply {
            isSelected = v == value
            setTextColor(getColor(if (v == value) R.color.ink else R.color.muted))
        }
    }

    private fun switchLanguage(lang: String) {
        if (lang == Lang.current(this)) return
        Lang.set(this, lang)
        Widgets.refreshAll(applicationContext)
        recreate()
    }

    companion object {
        const val PRAYER = 0
        const val MUHIB = 1
        const val EXTRA_SIDE = "side"
        private const val KEY_SIDE = "settings_side"
    }
}
