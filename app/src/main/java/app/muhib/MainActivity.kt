package app.muhib

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.text.NumberFormat
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Today: where you are, a card of prayer times you swipe through (Simple, Compact, Timeline,
 * Qibla), and the reminder underneath, blurred until you tap it. Tap the place's name to
 * switch, add or remove places.
 */
class MainActivity : Activity(), SensorEventListener {

    private var createdLang = ""
    private var createdAccent = ""
    private val clock = Handler(Looper.getMainLooper())
    private var resumed = false
    private var entered = false

    private fun <T : View> find(id: Int): T = findViewById(id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Look.wrap(Lang.wrap(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (WelcomeActivity.needed(this)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        Fonts.apply(this)
        setContentView(R.layout.activity_main)
        Accent.paint(window.decorView)
        Look.window(this)
        createdLang = Lang.current(this)
        createdAccent = Accent.key(this)

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            find<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                find<View>(R.id.scroll).setPadding(bars.left, bars.top, bars.right, bars.bottom)
                find<View>(R.id.statusShade).layoutParams.height = bars.top
                find<View>(R.id.sheet).apply { setPadding(paddingLeft, paddingTop, paddingRight, maxOf(bars.bottom, ime.bottom)) }
                sheetMaxHeight = find<View>(R.id.root).height - bars.top - dp(48) - maxOf(bars.bottom, ime.bottom)
                insets
            }
        }

        find<View>(R.id.placeButton).setOnClickListener { openSheet() }
        find<View>(R.id.openCalendar).setOnClickListener { startActivity(Intent(this, CalendarActivity::class.java)) }
        find<View>(R.id.openSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        find<View>(R.id.allow).setOnClickListener { askForLocation() }
        find<View>(R.id.chooseCity).setOnClickListener { openSheet(search = true) }
        find<View>(R.id.pin).setOnClickListener { WidgetPicker.show(this) }
        find<View>(R.id.calibLink).setOnClickListener {
            calibShown = !calibShown
            renderCalibration()
        }
        find<DayChart>(R.id.dayChart).onPick = { p ->
            compactPick = p.from to p.row
            showPick(p)
        }
        setUpPager()
        setUpReminder()
        setUpSheet()
        render()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (Lang.current(this) != createdLang || Accent.key(this) != createdAccent) return recreate()
        render()
        if (!entered) {
            entered = true
            entrance()
        }
        refreshData()
        tick()
        startCompass()
        startAmbient()
        Reminders.schedule(this)
        PrayerAlerts.schedule(this)
        askForNotifications()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        clock.removeCallbacksAndMessages(null)
        stopCompass()
        stopAmbient()
    }

    private fun entrance() {
        val views = listOf<View>(find(R.id.topBar), find(R.id.dateLine), find(R.id.prayerCard), find(R.id.stage))
        Motion.stagger(views, delay = 60, step = 90)
        if (!Muhib.isRevealed(this)) Motion.stagger(Motion.children(find(R.id.veil)), delay = 520, step = 140)
        Motion.stagger(listOf(find(R.id.more), find(R.id.pin), find(R.id.footer)), delay = 700, step = 120)
    }

    /** Redraws at the start of every minute while the app is open. */
    private fun tick() {
        clock.removeCallbacksAndMessages(null)
        val now = System.currentTimeMillis()
        clock.postDelayed({
            render()
            tick()
        }, 60_000 - now % 60_000 + 200)
    }

    /** Once: Android 13+ needs the user's OK before Muhib may show notifications. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < 33 || PrayerAlerts.allowed(this)) return
        if (!PrayerAlerts.on(this) && !Reminders.quietOn(this)) return
        if (Store.prefs(this).getBoolean(KEY_ASKED, false)) return
        Store.prefs(this).edit().putBoolean(KEY_ASKED, true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), ASK_NOTIFY)
    }

    // ---- data ---------------------------------------------------------------------------------

    private var locating = false
    private var lastLocate = 0L
    private var askedOnce = false
    private var gpsWeak = false

    /** Takes one location fix when showing where you are. Cities need nothing: their times are calculated. */
    private fun refreshData(force: Boolean = false) {
        if (Places.selectedId(this) != Places.HERE && Places.selected(this) != null) return
        if (!Locator.granted(this) || locating) return
        if (!force && System.currentTimeMillis() - lastLocate < 60_000) return
        locating = true
        lastLocate = System.currentTimeMillis()
        render()
        Refresh.here(this, done = { fresh ->
            locating = false
            gpsWeak = !fresh && Locator.enabled(this)
            if (!isDestroyed) render()
        })
    }

    private fun askForLocation() {
        val blocked = askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (blocked) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            askedOnce = true
            requestPermissions(Locator.permissions, ASK_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == ASK_NOTIFY) return PrayerAlerts.schedule(this)
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            Places.select(this, Places.HERE)
            render()
            refreshData(force = true)
        }
    }

    // ---- drawing ------------------------------------------------------------------------------

    private fun render() {
        renderReminder()
        val place = Places.selected(this)
        find<TextView>(R.id.footer).text = getString(
            R.string.footer, Store.methodName(this),
            getString(if (Store.school(this) == 1) R.string.asr_hanafi else R.string.asr_standard),
        )
        find<View>(R.id.pin).visibility =
            if (Widgets.placed(this).isEmpty() && Widgets.pinSupported(this)) View.VISIBLE else View.GONE
        val waiting = place == null && locating && Locator.granted(this)
        find<View>(R.id.viewEmpty).visibility = if (place == null && !waiting) View.VISIBLE else View.GONE
        find<View>(R.id.prayerCard).visibility = if (place == null) View.GONE else View.VISIBLE
        find<TextView>(R.id.status).apply {
            visibility = if (waiting) View.VISIBLE else View.GONE
            setText(R.string.locating)
        }
        if (place == null) {
            find<TextView>(R.id.placeLabel).text = getString(R.string.app_name)
            find<TextView>(R.id.placeName).text = getString(R.string.places)
            find<TextView>(R.id.dateLine).text = ""
            return
        }

        val schedule = Times.get(this, place)
        val zone = schedule.zone
        find<TextView>(R.id.placeName).text = place.name.ifEmpty { getString(R.string.current_location) }
        find<TextView>(R.id.placeLabel).text = when {
            place.isHere -> getString(R.string.current_location)
            zone.rules.getOffset(java.time.Instant.now()) != ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()) ->
                listOf(place.detail, getString(R.string.local_time, Fmt.time(this, System.currentTimeMillis(), zone)))
                    .filter { it.isNotEmpty() }.joinToString(" · ")
            else -> place.detail
        }

        val m = Moment(schedule)
        val hijriDay = m.hijri!!
        val hijri = Fmt.hijri(this, hijriDay).let { if (hijriDay !== m.day) getString(R.string.hijri_from_maghrib, it) else it }
        find<TextView>(R.id.dateLine).text = SpannableStringBuilder().apply {
            append(hijri)
            setSpan(ForegroundColorSpan(Accent.color(this@MainActivity)), 0, length, 0)
            append("  ·  ")
            append(Fmt.date(this@MainActivity, m.today))
        }
        if (m.day!!.online) find<TextView>(R.id.footer).append(" · " + getString(R.string.source_online))
        renderSimple(m)
        renderCompact(schedule, m.now)
        find<TextView>(R.id.compactLeft).text =
            getString(R.string.widget_left, Fmt.prayer(this, m.next), Fmt.left(this, m.nextAt - m.now))
        renderQibla(place)
    }

    private fun renderSimple(m: Moment) {
        val zone = m.schedule.zone
        find<TextView>(R.id.nextLabel).text = getString(R.string.next_in, Fmt.prayer(this, m.next))
        find<TextView>(R.id.nextLeft).text = Fmt.left(this, m.nextAt - m.now)
        find<TextView>(R.id.nextAt).text = getString(R.string.at_time, Fmt.time(this, m.nextAt, zone))
        find<NightBar>(R.id.simpleBar).progress =
            ((m.now - m.previousAt).toFloat() / (m.nextAt - m.previousAt)).coerceIn(0f, 1f)
        find<TextView>(R.id.since).text = getString(R.string.since, Fmt.prayer(this, m.previous), Fmt.time(this, m.previousAt, zone))
    }

    // ---- the swipeable card -----------------------------------------------------------------

    private fun setUpPager() {
        val pager = find<Pager>(R.id.pager)
        val dots = find<PageDots>(R.id.dots)
        dots.count = pager.childCount
        pager.onScroll = { dots.position = it }
        pager.onPage = { page ->
            Store.prefs(this).edit().putInt(KEY_PAGE, page).apply()
            if (page == QIBLA) startCompass() else stopCompass()
            find<View>(R.id.prayerCard).performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        val start = Store.prefs(this).getInt(KEY_PAGE, COMPACT)
        pager.post { pager.setPage(start, animate = false) }
        dots.position = start.toFloat()
    }

    // ---- compact and timeline -----------------------------------------------------------------

    private var compactZone: ZoneId = ZoneId.systemDefault()
    private var compactNow = 0L
    private var compactPick: Pair<Long, Int>? = null

    /**
     * The whole day on one timeline, from just before Fajr to just before the next Fajr.
     * Until half an hour before Fajr, the night still belongs to yesterday's chart.
     */
    private fun renderCompact(s: Schedule, now: Long) {
        val zone = s.zone
        val today = Moment(s, now)
        val date = if (now < today.day!!.imsak - LEAD) today.today.minusDays(1) else today.today
        val d = s.day(date)
        val prev = s.day(date.minusDays(1))
        val next = s.day(date.plusDays(1))
        val dm = Moment(s, d.imsak)
        val start = d.imsak - LEAD
        val end = next.imsak + LEAD
        val (rise, zenith, set) = dm.forbidden
        val ishraq = rise.second
        val duha = ishraq + 20 * Moment.MIN
        val names = resources.getStringArray(R.array.prayers)
        val fajr = getString(R.string.fajr)
        val avoid = getString(R.string.forbidden_short)

        // The day line: each part runs until the next one starts.
        class Mark(val label: String, val name: String, val kind: Int, val at: Long, val low: Boolean = false)
        val marks = listOf(
            Mark(names[Day.ISHA], names[Day.ISHA], DayChart.PRAYER, prev.t[Day.ISHA], low = true),
            Mark(fajr, fajr, DayChart.PRAYER, d.imsak),
            Mark(avoid, getString(R.string.forbidden_sunrise), DayChart.FORBIDDEN, rise.first),
            Mark(getString(R.string.ishraq), getString(R.string.ishraq), DayChart.PRAYER, ishraq),
            Mark(getString(R.string.duha), getString(R.string.duha), DayChart.PRAYER, duha),
            Mark(avoid, getString(R.string.forbidden_zenith), DayChart.FORBIDDEN, zenith.first),
            Mark(names[Day.DHUHR], names[Day.DHUHR], DayChart.PRAYER, d.dhuhr),
            Mark(names[Day.ASR], names[Day.ASR], DayChart.PRAYER, d.t[Day.ASR]),
            Mark(avoid, getString(R.string.forbidden_sunset), DayChart.FORBIDDEN, set.first),
            Mark(names[Day.MAGHRIB], names[Day.MAGHRIB], DayChart.PRAYER, d.maghrib, low = true),
            Mark(names[Day.ISHA], names[Day.ISHA], DayChart.PRAYER, d.t[Day.ISHA], low = true),
            Mark(fajr, fajr, DayChart.PRAYER, next.imsak),
        )
        val parts = mutableListOf<DayChart.Part>()
        var at = Long.MIN_VALUE
        marks.forEachIndexed { i, mk ->
            val from = maxOf(mk.at, at)
            val to = if (i + 1 < marks.size) marks[i + 1].at else next.sunrise
            if (to > from) parts += DayChart.Part(mk.label, mk.name, from, to, mk.kind, low = mk.low)
            at = maxOf(at, to)
        }

        // The night rows, for last night's end and tonight: whole, in thirds, in halves with Seher.
        val nightLabel = getString(R.string.night_short)
        for ((ns, ne) in listOf(prev.maghrib to d.imsak, d.maghrib to next.imsak)) {
            fun f(x: Double) = ns + ((ne - ns) * x).toLong()
            parts += DayChart.Part(nightLabel, getString(R.string.prayer_night), ns, ne, DayChart.NIGHT, row = 1)
            parts += DayChart.Part("1/3", getString(R.string.night_first), ns, f(1 / 3.0), DayChart.NIGHT, row = 2)
            parts += DayChart.Part("2/3", getString(R.string.night_second), f(1 / 3.0), f(2 / 3.0), DayChart.NIGHT, row = 2, alt = true)
            parts += DayChart.Part("3/3", getString(R.string.night_last), f(2 / 3.0), ne, DayChart.NIGHT, row = 2)
            parts += DayChart.Part("1/2", getString(R.string.night_half_first), ns, f(0.5), DayChart.NIGHT, row = 3)
            parts += DayChart.Part("2/2", getString(R.string.night_half_second), f(0.5), f(5 / 6.0), DayChart.NIGHT, row = 3, alt = true)
            parts += DayChart.Part(getString(R.string.seher), getString(R.string.seher_name), f(5 / 6.0), ne, DayChart.NIGHT, row = 3)
        }
        val shown = parts.filter { it.to > start && it.from < end }
        val current = shown.firstOrNull { it.row == 0 && now >= it.from && now < it.to }

        val chart = find<DayChart>(R.id.dayChart)
        chart.show(shown, start, end, now, zone, current?.let { if (it.kind == DayChart.FORBIDDEN) avoid else it.label }.orEmpty(),
            android.text.format.DateFormat.is24HourFormat(this))
        compactZone = zone
        compactNow = now
        val pick = compactPick?.let { (from, row) -> shown.firstOrNull { it.from == from && it.row == row } } ?: current
        chart.picked = pick
        pick?.let { showPick(it) }

        // The six times on the Compact page; the extra ones of the day and the night under the timeline.
        fun f(x: Double) = d.maghrib + ((next.imsak - d.maghrib) * x).toLong()
        val extras = listOf(
            listOf(getString(R.string.ishraq) to ishraq, getString(R.string.duha) to duha, getString(R.string.evvabin) to d.maghrib),
            listOf(getString(R.string.night_third_1) to f(1 / 3.0), getString(R.string.midnight_short) to f(0.5),
                getString(R.string.night_third_2) to f(2 / 3.0), getString(R.string.seher) to f(5 / 6.0)),
        )
        // Like the widget: the prayer time we are in is the one in the accent.
        val inNow = d.t[if (date == today.today) today.current else Day.ISHA]
        val group = find<LinearLayout>(R.id.compactTimes)
        group.removeAllViews()
        cellRow(group, (0..5).map { names[it] to d.t[it] }, zone, now, inNow)
        val more = find<LinearLayout>(R.id.timelineTimes)
        more.removeAllViews()
        extras.forEach { cellRow(more, it, zone, now, null) }

        find<TextView>(R.id.compactLegend).text = SpannableStringBuilder().apply {
            fun dot(color: Int) {
                val start = length
                append("●  ")
                setSpan(ForegroundColorSpan(getColor(color)), start, start + 1, 0)
            }
            dot(R.color.forbidden_ink); append(getString(R.string.forbidden)); append("     ")
            dot(R.color.night); append(getString(R.string.prayer_night)); append("\n")
            append(getString(R.string.compact_hint))
        }
    }

    private fun showPick(p: DayChart.Part) {
        val now = compactNow
        find<TextView>(R.id.compactPick).text = p.name
        val range = Fmt.time(this, p.from, compactZone) + " – " + Fmt.time(this, p.to, compactZone)
        val state = when {
            now < p.from -> getString(R.string.compact_in, Fmt.left(this, p.from - now))
            now < p.to -> getString(R.string.compact_left, Fmt.left(this, p.to - now))
            else -> getString(R.string.compact_past)
        }
        find<TextView>(R.id.compactPickSub).text = "$range · $state"
        find<View>(R.id.dayChart).contentDescription = "${p.name}, $range, $state"
    }

    /** A row of equal cells, each a name over a time; [current] in the accent colour. */
    private fun cellRow(group: LinearLayout, cells: List<Pair<String, Long>>, zone: ZoneId, now: Long, current: Long?) {
        if (group.childCount > 0) group.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.hairline))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                marginStart = dp(20); marginEnd = dp(20)
            }
        })
        val medium = Fonts.medium(this)
        val row = LinearLayout(this).apply { setPadding(dp(8), dp(12), dp(8), dp(12)) }
        cells.forEach { (label, at) ->
            val lit = at == current
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            cell.addView(TextView(this).apply {
                text = label
                gravity = android.view.Gravity.CENTER
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (lit) Accent.color(context) else getColor(R.color.muted))
            })
            cell.addView(TextView(this).apply {
                text = Fmt.time(this@MainActivity, at, zone, short = true)
                gravity = android.view.Gravity.CENTER
                textSize = 16f
                typeface = medium
                setPadding(0, dp(2), 0, 0)
                setTextColor(when { lit -> Accent.color(context); at <= now -> getColor(R.color.muted); else -> getColor(R.color.ink) })
            })
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        group.addView(row)
    }

    // ---- qibla --------------------------------------------------------------------------------

    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val rotationSensor by lazy { sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    private var compassOn = false
    private var heading = Float.NaN
    private var headingX = 1.0
    private var headingY = 0.0
    private var declination = 0f
    private var wasFacing = false
    private var compassAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var calibShown = false

    private fun renderQibla(place: Place) {
        val bearing = Qibla.bearing(place.lat, place.lng)
        val km = NumberFormat.getIntegerInstance(java.util.Locale(Lang.current(this)))
            .format(Qibla.distanceToKaaba(place.lat, place.lng).roundToInt())
        find<QiblaDial>(R.id.dial).qibla = bearing.toFloat()
        find<TextView>(R.id.qiblaDeg).text = "${bearing.roundToInt()}°"
        find<TextView>(R.id.qiblaSub).text = getString(R.string.qibla_from_north, km)
        find<TextView>(R.id.qiblaFor).apply {
            visibility = if (place.isHere) View.GONE else View.VISIBLE
            text = getString(R.string.qibla_other_place, place.name)
        }
        declination = GeomagneticField(place.lat.toFloat(), place.lng.toFloat(), 0f, System.currentTimeMillis()).declination
        updateQiblaHint()
        renderLocationStatus(place)
        renderCalibration()
    }

    /** Says where the location came from, and what to do when GPS is weak, old or off. */
    private fun renderLocationStatus(place: Place) {
        val box = find<View>(R.id.locStatus)
        val text = find<TextView>(R.id.locText)
        val action = find<TextView>(R.id.locAction)
        if (!place.isHere) return run { box.visibility = View.GONE }
        box.visibility = View.VISIBLE
        val age = System.currentTimeMillis() - place.fixedAt
        val ago = Fmt.ago(this, place.fixedAt)
        var warn = true
        var act: Pair<Int, () -> Unit>? = null
        text.text = when {
            !Locator.granted(this) -> {
                act = R.string.allow_location to { askForLocation() }
                getString(R.string.loc_denied)
            }
            !Locator.enabled(this) -> {
                act = R.string.loc_turn_on to { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                getString(R.string.loc_off, ago)
            }
            locating -> {
                warn = false
                getString(R.string.loc_finding)
            }
            gpsWeak -> {
                act = R.string.try_again to { refreshData(force = true) }
                getString(R.string.loc_weak, ago)
            }
            age > OLD_FIX -> {
                act = R.string.loc_update to { refreshData(force = true) }
                getString(R.string.loc_old, ago)
            }
            place.accuracy > ROUGH_FIX -> {
                act = R.string.loc_update to { refreshData(force = true) }
                getString(R.string.loc_rough, Fmt.distance(this, place.accuracy))
            }
            else -> {
                warn = false
                getString(R.string.loc_good, Fmt.distance(this, place.accuracy), ago)
            }
        }
        box.setBackgroundResource(if (warn) R.drawable.note_bg else 0)
        if (warn) Accent.paint(box)
        text.setTextColor(getColor(if (warn) R.color.ink else R.color.muted))
        action.visibility = if (act == null) View.GONE else View.VISIBLE
        act?.let { (label, run) ->
            action.setText(label)
            action.setOnClickListener { run() }
        }
    }

    /** The figure-8 card: shown when the compass reports low accuracy, or when asked for. */
    private fun renderCalibration() {
        val needed = rotationSensor != null && compassAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        val card = find<View>(R.id.calibCard)
        val show = rotationSensor != null && (needed || calibShown)
        if (show && card.visibility != View.VISIBLE) Motion.settle(card)
        card.visibility = if (show) View.VISIBLE else View.GONE
        find<TextView>(R.id.calibTitle).setText(if (needed) R.string.calib_needed else R.string.calib_title)
        find<View>(R.id.calibLink).visibility = if (rotationSensor == null || needed) View.GONE else View.VISIBLE
        find<TextView>(R.id.calibLink).setText(if (calibShown) R.string.calib_hide else R.string.calib_link)
    }

    private fun updateQiblaHint() {
        val dial = find<QiblaDial>(R.id.dial)
        find<TextView>(R.id.qiblaHint).apply {
            text = getString(when {
                rotationSensor == null -> R.string.qibla_no_compass
                dial.facing && !heading.isNaN() -> R.string.qibla_facing
                else -> R.string.qibla_hint
            })
            setTextColor(if (dial.facing && !heading.isNaN()) Accent.color(this@MainActivity) else getColor(R.color.muted))
        }
    }

    /** The compass runs only while the Qibla page is on screen. */
    private fun startCompass() {
        if (compassOn || find<Pager>(R.id.pager).page != QIBLA || rotationSensor == null || !resumed) return
        compassOn = sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopCompass() {
        if (!compassOn) return
        sensors.unregisterListener(this)
        compassOn = false
    }

    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.accuracy != compassAccuracy) onAccuracyChanged(event.sensor, event.accuracy)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        @Suppress("DEPRECATION")
        val rotation = if (Build.VERSION.SDK_INT >= 30) display?.rotation ?: 0 else windowManager.defaultDisplay.rotation
        val (x, y) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, x, y, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val raw = Math.toRadians(Math.toDegrees(orientation[0].toDouble()) + declination)
        // Average the direction as a unit vector, so readings near north (359° and 1°) average
        // correctly, then ignore wobbles under half a degree. The dial glides to the result.
        if (heading.isNaN()) { headingX = kotlin.math.cos(raw); headingY = kotlin.math.sin(raw) }
        headingX += (kotlin.math.cos(raw) - headingX) * SMOOTHING
        headingY += (kotlin.math.sin(raw) - headingY) * SMOOTHING
        val target = ((Math.toDegrees(kotlin.math.atan2(headingY, headingX)).toFloat()) + 360) % 360
        if (!heading.isNaN() && kotlin.math.abs(((target - heading + 540) % 360) - 180) < 0.5f) return
        heading = target
        val dial = find<QiblaDial>(R.id.dial)
        dial.heading = heading
        if (dial.facing && !wasFacing) dial.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (dial.facing != wasFacing) {
            wasFacing = dial.facing
            updateQiblaHint()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val wasNeeded = compassAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        compassAccuracy = accuracy
        if (wasNeeded && accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            // Calibrated: say so briefly, then tuck the card away.
            calibShown = true
            find<TextView>(R.id.calibTitle).setText(R.string.calib_done)
            clock.postDelayed({ calibShown = false; renderCalibration() }, 2500)
            find<View>(R.id.calibLink).visibility = View.GONE
            return
        }
        renderCalibration()
    }

    // ---- the reminder -----------------------------------------------------------------------

    private lateinit var card: View
    private lateinit var content: ViewGroup
    private lateinit var veil: ViewGroup
    private lateinit var veilBlur: ImageView
    private lateinit var more: LinearLayout
    private var revealing = false
    private val ambient = mutableListOf<Animator>()

    private fun setUpReminder() {
        card = find(R.id.card)
        content = find(R.id.content)
        veil = find(R.id.veil)
        veilBlur = find(R.id.veilBlur)
        more = find(R.id.more)
        // When "a little more" appears, what's below glides down instead of jumping.
        find<ViewGroup>(R.id.page).layoutTransition = LayoutTransition().apply {
            disableTransitionType(LayoutTransition.APPEARING)
            disableTransitionType(LayoutTransition.DISAPPEARING)
            setInterpolator(LayoutTransition.CHANGE_APPEARING, Motion.ease)
            setDuration(LayoutTransition.CHANGE_APPEARING, 600)
        }
        card.setOnClickListener { if (!Muhib.isRevealed(this) && !revealing) revealHere() }
        find<View>(R.id.share).setOnClickListener { share(Muhib.current(this)) }
        find<View>(R.id.coverAgain).setOnClickListener {
            Muhib.coverAgain(this)
            Widgets.refreshAll(this)
            renderReminder()
            Motion.stagger(Motion.children(veil), delay = 150, step = 140)
            startAmbient()
        }
    }

    private fun renderReminder() {
        val reminder = Muhib.current(this)
        val revealed = Muhib.isRevealed(this)
        find<TextView>(R.id.headline).text = reminder.headline
        find<TextView>(R.id.arabic).apply {
            text = reminder.arabic
            visibility = if (reminder.arabic == null) View.GONE else View.VISIBLE
        }
        find<TextView>(R.id.text).text = reminder.text
        find<TextView>(R.id.source).text = reminder.source
        find<TextView>(R.id.moreText).apply {
            text = reminder.more
            visibility = if (reminder.more.isEmpty()) View.GONE else View.VISIBLE
        }
        find<TextView>(R.id.sourceFull).text = reminder.source
        find<TextView>(R.id.openSource).apply {
            visibility = if (reminder.link == null) View.GONE else View.VISIBLE
            setOnClickListener { reminder.link?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
        }
        find<ImageView>(R.id.veilStar).setColorFilter(Accent.color(this))
        if (!revealing) setCovered(!revealed, reminder)
    }

    private fun setCovered(covered: Boolean, reminder: Reminder) {
        // Covered, the card keeps a calm fixed height; revealed, it fits the words.
        card.layoutParams = card.layoutParams.apply { height = if (covered) dp(COVERED_HEIGHT) else ViewGroup.LayoutParams.WRAP_CONTENT }
        veil.visibility = if (covered) View.VISIBLE else View.GONE
        veil.alpha = 1f
        veil.translationY = 0f
        if (!covered) card.translationZ = 0f
        more.visibility = if (covered) View.GONE else View.VISIBLE
        content.importantForAccessibility =
            if (covered) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (Build.VERSION.SDK_INT >= 31) {
            content.setRenderEffect(if (covered) blur(BLUR_RADIUS) else null)
            content.alpha = if (covered) 0.4f else 1f
        } else {
            // No live blur before Android 12: use the same blurred image as the widget.
            content.alpha = if (covered) 0f else 1f
            veilBlur.alpha = 1f
            veilBlur.visibility = if (covered) View.VISIBLE else View.GONE
            if (covered) {
                veilBlur.setImageBitmap(Blur.render(reminder))
                veilBlur.setColorFilter((Accent.color(this) and 0x00FFFFFF) or 0x4D000000)
            }
        }
    }

    private fun startAmbient() {
        if (ambient.isNotEmpty() || Muhib.isRevealed(this) || !resumed) return
        ambient += Motion.lift(card)
        ambient += Motion.spin(find(R.id.veilStar))
        ambient.forEach { it.start() }
    }

    private fun stopAmbient() {
        ambient.forEach { it.cancel() }
        ambient.clear()
    }

    private fun revealHere() {
        card.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        Muhib.reveal(this)
        Widgets.refreshAll(this)
        revealing = true
        stopAmbient()
        content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO

        val lift = -14f * resources.displayMetrics.density
        veil.animate().alpha(0f).translationY(lift).setStartDelay(0).setDuration(450).setInterpolator(Motion.ease).start()
        card.animate().translationZ(0f).setDuration(900).setInterpolator(Motion.ease).start()

        // The card opens up to the full passage while the blur lifts.
        content.measure(View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val from = card.height
        val to = maxOf(content.measuredHeight, card.minimumHeight)
        ValueAnimator.ofInt(from, to).apply {
            duration = 900
            startDelay = 80
            interpolator = Motion.ease
            addUpdateListener { card.layoutParams = card.layoutParams.apply { height = it.animatedValue as Int } }
            start()
        }

        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 1300
            startDelay = 120
            interpolator = Motion.ease
            addUpdateListener {
                val t = it.animatedValue as Float
                if (Build.VERSION.SDK_INT >= 31) {
                    content.setRenderEffect(if (t > 0.01f) blur(BLUR_RADIUS * t) else null)
                    content.alpha = 1f - 0.6f * t
                } else {
                    veilBlur.alpha = t
                    content.alpha = 1f - t
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    revealing = false
                    renderReminder()
                    Motion.stagger(Motion.children(more), delay = 100, step = 120)
                }
            })
            start()
        }
    }

    private fun blur(radius: Float): RenderEffect =
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)

    private fun share(reminder: Reminder) {
        val body = listOfNotNull(reminder.arabic, reminder.text, "— ${reminder.source}").joinToString("\n\n")
        startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), null)
        )
    }

    // ---- places sheet -------------------------------------------------------------------------

    private var sheetOpen = false
    private var sheetMaxHeight = 0
    private var searchTicket = 0
    private val searchDelay = Handler(Looper.getMainLooper())
    private val closeOnBack by lazy { OnBackInvokedCallback { closeSheet() } }

    private fun setUpSheet() {
        find<View>(R.id.scrim).setOnClickListener { closeSheet() }
        val query = find<EditText>(R.id.query)
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                find<View>(R.id.clearQuery).visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
                searchDelay.removeCallbacksAndMessages(null)
                searchDelay.postDelayed({ search(s.toString().trim()) }, 250)
            }
        })
        find<View>(R.id.clearQuery).setOnClickListener { query.setText("") }
        // Drag the handle area down to close.
        dragToClose(find(R.id.sheet))
    }

    private fun openSheet(search: Boolean = false) {
        if (sheetOpen) return
        sheetOpen = true
        val sheet = find<View>(R.id.sheet)
        val scrim = find<View>(R.id.scrim)
        find<EditText>(R.id.query).setText("")
        buildPlaces()
        scrim.visibility = View.VISIBLE
        sheet.visibility = View.VISIBLE
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.animate().translationY(0f).setDuration(420).setInterpolator(Motion.ease).start()
        }
        scrim.animate().alpha(1f).setDuration(300).start()
        Motion.stagger(Motion.children(find(R.id.placeList)), delay = 160, step = 45, rise = 8f)
        if (search) sheet.postDelayed({ focusSearch() }, 350)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, closeOnBack)
        }
    }

    private fun closeSheet() {
        if (!sheetOpen) return
        sheetOpen = false
        val sheet = find<View>(R.id.sheet)
        val scrim = find<View>(R.id.scrim)
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(sheet.windowToken, 0)
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(300).setInterpolator(Motion.ease)
            .withEndAction { sheet.visibility = View.GONE }.start()
        scrim.animate().alpha(0f).setDuration(260).withEndAction { scrim.visibility = View.GONE }.start()
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.unregisterOnBackInvokedCallback(closeOnBack)
    }

    @Deprecated("Only reached below Android 13")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (sheetOpen) closeSheet() else super.onBackPressed()
    }

    private fun focusSearch() {
        val query = find<EditText>(R.id.query)
        query.requestFocus()
        getSystemService(InputMethodManager::class.java).showSoftInput(query, 0)
    }

    /** Keeps the list from growing past the screen; it scrolls inside the sheet instead. */
    private fun limitList() {
        val scroll = find<ScrollView>(R.id.sheetScroll)
        val list = find<View>(R.id.placeList)
        list.measure(View.MeasureSpec.makeMeasureSpec(scroll.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val max = ((if (sheetMaxHeight > 0) sheetMaxHeight else resources.displayMetrics.heightPixels * 3 / 4) - dp(220)).coerceAtLeast(dp(160))
        scroll.layoutParams = scroll.layoutParams.apply {
            height = if (list.measuredHeight > max) max else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    /** Where you are, then your cities, each with its next prayer. */
    private fun buildPlaces() {
        val list = find<LinearLayout>(R.id.placeList)
        list.removeAllViews()
        find<View>(R.id.sheetMessage).visibility = View.GONE
        find<View>(R.id.sheetHint).visibility = if (Places.cities(this).isEmpty()) View.GONE else View.VISIBLE
        val selected = Places.selectedId(this)
        addPlaceRow(list, Places.here(this), Places.HERE, selected == Places.HERE)
        Places.cities(this).forEach { addPlaceRow(list, it, it.id, selected == it.id) }
        list.post { limitList() }
    }

    private fun addPlaceRow(list: LinearLayout, place: Place?, id: String, selected: Boolean) {
        val r = LayoutInflater.from(this).inflate(R.layout.row_place, list, false)
        Accent.paint(r)
        r.isSelected = selected
        val icon = r.findViewById<ImageView>(R.id.icon)
        icon.setImageResource(if (id == Places.HERE) R.drawable.ic_pin else R.drawable.ic_globe)
        Accent.tint(icon, id == Places.HERE)
        r.findViewById<TextView>(R.id.name).text = place?.name?.ifEmpty { null } ?: getString(R.string.current_location)
        r.findViewById<TextView>(R.id.detail).text = when {
            place == null -> getString(if (Locator.granted(this)) R.string.locating else R.string.allow_location)
            else -> nextLine(place)
        }
        r.findViewById<ImageView>(R.id.remove).apply {
            if (selected) {
                setImageResource(R.drawable.ic_check)
                Accent.tint(this, true)
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } else if (id == Places.HERE) {
                visibility = View.INVISIBLE
            } else {
                alpha = 0.6f
                setOnClickListener { removePlace(r, id) }
            }
        }
        r.setOnClickListener { choose(id) }
        if (id != Places.HERE) swipeToRemove(r, id)
        list.addView(r)
    }

    private fun choose(id: String) {
        if (id == Places.HERE && !Locator.granted(this)) {
            closeSheet()
            return askForLocation()
        }
        Places.select(this, id)
        closeSheet()
        compactPick = null
        render()
        refreshData(force = id == Places.HERE)
        Widgets.refreshAll(this)
        Motion.stagger(listOf(find(R.id.topBar), find(R.id.dateLine), find(R.id.prayerCard)), step = 70, rise = 10f)
    }

    private fun removePlace(row: View, id: String) {
        row.animate().alpha(0f).translationX(row.width.toFloat()).setDuration(220).setInterpolator(Motion.ease).withEndAction {
            Places.remove(this, id)
            (row.parent as? ViewGroup)?.removeView(row)
            render()
            Widgets.refreshAll(this)
            if (Places.cities(this).isEmpty()) find<View>(R.id.sheetHint).visibility = View.GONE
            // The removed place might have been the selected one: redraw the checks.
            buildPlaces()
        }.start()
    }

    /** A city row follows the finger sideways; let go past a third of the way and it's removed. */
    @SuppressLint("ClickableViewAccessibility")
    private fun swipeToRemove(row: View, id: String) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var dragging = false
        row.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    if (!dragging && abs(dx) > slop && abs(dx) > abs(e.rawY - downY)) {
                        dragging = true
                        v.parent.requestDisallowInterceptTouchEvent(true)
                        v.isPressed = false
                    }
                    if (dragging) {
                        v.translationX = dx
                        v.alpha = 1f - (abs(dx) / v.width).coerceIn(0f, 0.7f)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    if (abs(v.translationX) > v.width / 3f) {
                        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        val out = if (v.translationX > 0) v.width.toFloat() else -v.width.toFloat()
                        v.animate().translationX(out).alpha(0f).setDuration(180).withEndAction {
                            Places.remove(this, id)
                            render()
                            Widgets.refreshAll(this)
                            buildPlaces()
                        }.start()
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(Motion.ease).start()
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    /** The handle and title drag the sheet; let go past a quarter and it closes. */
    @SuppressLint("ClickableViewAccessibility")
    private fun dragToClose(sheet: View) {
        var downY = 0f
        val grab = View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> { sheet.translationY = (e.rawY - downY).coerceAtLeast(0f); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (sheet.translationY > sheet.height / 4f) closeSheet()
                    else sheet.animate().translationY(0f).setDuration(220).setInterpolator(Motion.ease).start()
                    true
                }
                else -> false
            }
        }
        find<View>(R.id.handle).setOnTouchListener(grab)
        (find<ViewGroup>(R.id.sheet).getChildAt(1)).setOnTouchListener(grab)
    }

    private fun search(text: String) {
        val ticket = ++searchTicket
        val message = find<TextView>(R.id.sheetMessage)
        if (text.length < 2) {
            if (text.isEmpty() && sheetOpen) buildPlaces()
            return
        }
        Refresh.run {
            val found = Cities.search(this, text).map { Cities.place(this, it) }
            Refresh.post {
                if (ticket != searchTicket || isDestroyed || !sheetOpen) return@post
                val list = find<LinearLayout>(R.id.placeList)
                list.removeAllViews()
                find<View>(R.id.sheetHint).visibility = View.GONE
                message.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
                message.setText(R.string.no_results)
                found.forEach { place ->
                    val r = LayoutInflater.from(this).inflate(R.layout.row_place, list, false)
                    Accent.paint(r)
                    r.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_globe)
                    r.findViewById<TextView>(R.id.name).text = place.name
                    r.findViewById<TextView>(R.id.detail).text = place.detail
                    r.findViewById<ImageView>(R.id.remove).apply {
                        setImageResource(R.drawable.ic_plus)
                        Accent.tint(this, true)
                        setOnClickListener { addCity(place) }
                    }
                    r.setOnClickListener { addCity(place) }
                    list.addView(r)
                }
                list.post { limitList() }
                Motion.stagger(Motion.children(list), step = 30, rise = 6f)
            }
        }
    }

    private fun addCity(place: Place) {
        Places.add(this, place)
        find<EditText>(R.id.query).setText("")
        choose(place.id)
    }

    /** "Asr in 1h 2m" for a place. */
    private fun nextLine(place: Place): String {
        val m = Moment(Times.get(this, place))
        return getString(R.string.widget_left, Fmt.prayer(this, m.next), Fmt.left(this, m.nextAt - m.now))
    }

    companion object {
        const val SIMPLE = 0
        const val COMPACT = 1
        const val TIMELINE = 2
        const val QIBLA = 3
        const val KEY_ASKED = "asked_notifications"
        private const val KEY_PAGE = "page"
        private const val ASK_LOCATION = 1
        private const val ASK_NOTIFY = 2
        private const val LEAD = 30 * Moment.MIN
        private const val OLD_FIX = 6 * 3_600_000L
        private const val ROUGH_FIX = 5_000f
        private const val BLUR_RADIUS = 40f
        /** How much of each new compass reading counts (with readings about every 20 ms). */
        private const val SMOOTHING = 0.08
        private const val COVERED_HEIGHT = 250
    }
}
