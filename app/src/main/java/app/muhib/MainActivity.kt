package app.muhib

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date

class MainActivity : Activity() {

    private lateinit var card: View
    private lateinit var content: ViewGroup
    private lateinit var veil: ViewGroup
    private lateinit var veilBlur: ImageView
    private lateinit var more: LinearLayout
    private var revealing = false
    private var entered = false
    private var createdLang = ""
    private val ambient = mutableListOf<Animator>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (WelcomeActivity.needed(this)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        createdLang = Lang.current(this)

        card = findViewById(R.id.card)
        content = findViewById(R.id.content)
        veil = findViewById(R.id.veil)
        veilBlur = findViewById(R.id.veilBlur)
        more = findViewById(R.id.more)

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            findViewById<View>(R.id.scroll).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        // When "a little more" appears, the sections below glide down instead of jumping.
        findViewById<ViewGroup>(R.id.page).layoutTransition = LayoutTransition().apply {
            disableTransitionType(LayoutTransition.APPEARING)
            disableTransitionType(LayoutTransition.DISAPPEARING)
            setInterpolator(LayoutTransition.CHANGE_APPEARING, Motion.ease)
            setDuration(LayoutTransition.CHANGE_APPEARING, 600)
        }

        findViewById<TextView>(R.id.hijri).text = hijriDate()

        card.setOnClickListener { if (!Muhib.isRevealed(this) && !revealing) revealHere() }
        findViewById<View>(R.id.share).setOnClickListener { share(Muhib.current(this)) }
        findViewById<View>(R.id.openSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.pin).setOnClickListener {
            getSystemService(AppWidgetManager::class.java)
                .requestPinAppWidget(ComponentName(this, MuhibWidget::class.java), null, null)
        }
        findViewById<View>(R.id.coverAgain).setOnClickListener {
            Muhib.coverAgain(this)
            MuhibWidget.refreshAll(this)
            findViewById<View>(R.id.scroll).scrollTo(0, 0)
            render()
            Motion.stagger(Motion.children(veil), delay = 150, step = 140)
            startAmbient()
        }

        if (Reminders.quietOn(this) && !Muhib.prefs(this).getBoolean(KEY_ASKED, false)) askForNotifications()
        Reminders.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Settings in another language: rebuild with the new words.
        if (Lang.current(this) != createdLang) {
            recreate()
            return
        }
        render()
        if (!entered) {
            entered = true
            entrance()
        }
        startAmbient()
    }

    override fun onPause() {
        super.onPause()
        stopAmbient()
    }

    private fun entrance() {
        val covered = !Muhib.isRevealed(this)
        Motion.stagger(listOf(findViewById(R.id.topBar)), step = 0)
        Motion.settle(card, delay = 120)
        if (covered) {
            Motion.stagger(Motion.children(veil), delay = 450, step = 150)
            Motion.stagger(listOf(findViewById(R.id.footer)), delay = 1000)
        } else {
            Motion.stagger(Motion.children(content), delay = 350, step = 130)
            Motion.stagger(listOf(more, findViewById(R.id.footer)), delay = 950, step = 160)
        }
    }

    private fun render() {
        val reminder = Muhib.current(this)
        val revealed = Muhib.isRevealed(this)

        findViewById<TextView>(R.id.headline).text = reminder.headline
        findViewById<TextView>(R.id.arabic).apply {
            text = reminder.arabic
            visibility = if (reminder.arabic == null) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.text).text = reminder.text
        findViewById<TextView>(R.id.source).text = reminder.source
        findViewById<TextView>(R.id.moreText).apply {
            text = reminder.more
            visibility = if (reminder.more.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.sourceFull).text = reminder.source
        findViewById<TextView>(R.id.openSource).apply {
            visibility = if (reminder.link == null) View.GONE else View.VISIBLE
            setOnClickListener { reminder.link?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
        }

        val manager = getSystemService(AppWidgetManager::class.java)
        findViewById<TextView>(R.id.pin).visibility =
            if (!MuhibWidget.hasWidgets(this) && manager.isRequestPinAppWidgetSupported) View.VISIBLE else View.GONE

        if (!revealing) setCovered(!revealed, reminder)
    }

    private fun setCovered(covered: Boolean, reminder: Reminder) {
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
            if (covered) veilBlur.setImageBitmap(Blur.render(reminder))
        }
    }

    private fun startAmbient() {
        if (ambient.isNotEmpty() || Muhib.isRevealed(this)) return
        ambient += Motion.lift(card)
        ambient += Motion.spin(findViewById(R.id.veilStar))
        ambient.forEach { it.start() }
    }

    private fun stopAmbient() {
        ambient.forEach { it.cancel() }
        ambient.clear()
    }

    private fun revealHere() {
        card.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        Muhib.reveal(this)
        MuhibWidget.refreshAll(this)
        revealing = true
        stopAmbient()
        content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO

        val lift = -14f * resources.displayMetrics.density
        veil.animate().alpha(0f).translationY(lift).setStartDelay(0).setDuration(450).setInterpolator(Motion.ease).start()
        card.animate().translationZ(0f).setDuration(900).setInterpolator(Motion.ease).start()

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
                    render()
                    Motion.stagger(Motion.children(more), delay = 100, step = 120)
                }
            })
            start()
        }
    }

    private fun blur(radius: Float): RenderEffect =
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)

    private fun hijriDate(): String {
        val now = Date()
        val lang = Lang.current(this)
        val weekday = SimpleDateFormat("EEEE", ULocale(lang)).format(now)
        val hijri = SimpleDateFormat("d MMMM y", ULocale("$lang@calendar=islamic-umalqura")).format(now)
        return "$weekday · $hijri"
    }

    private fun share(reminder: Reminder) {
        val body = listOfNotNull(reminder.arabic, reminder.text, "— ${reminder.source}").joinToString("\n\n")
        startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), null)
        )
    }

    private fun askForNotifications() {
        Muhib.prefs(this).edit().putBoolean(KEY_ASKED, true).apply()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    companion object {
        const val KEY_ASKED = "asked_notifications"
        private const val BLUR_RADIUS = 40f
    }
}
