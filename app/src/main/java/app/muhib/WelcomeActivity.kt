package app.muhib

import android.Manifest
import android.animation.Animator
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.TextView

/**
 * First run: greet, show what the widget does, help put it on the home screen.
 * Steps: hello -> widget -> done. The done step appears as soon as a widget is placed.
 */
class WelcomeActivity : Activity() {

    private lateinit var steps: List<ViewGroup>
    private var step = HELLO
    private val ambient = mutableListOf<Animator>()
    private val demo = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        steps = listOf(findViewById(R.id.stepHello), findViewById(R.id.stepWidget), findViewById(R.id.stepDone))

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            findViewById<View>(R.id.scroll).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        val sample = Content.byId(this, SAMPLE_ID) ?: Muhib.current(this)
        findViewById<ImageView>(R.id.previewBlur).setImageBitmap(Blur.render(sample))
        findViewById<TextView>(R.id.previewArabic).text = sample.arabic
        findViewById<TextView>(R.id.previewText).text = sample.text

        val pinSupported = getSystemService(AppWidgetManager::class.java).isRequestPinAppWidgetSupported
        findViewById<View>(R.id.addWidget).apply {
            visibility = if (pinSupported) View.VISIBLE else View.GONE
            setOnClickListener {
                getSystemService(AppWidgetManager::class.java)
                    .requestPinAppWidget(ComponentName(this@WelcomeActivity, MuhibWidget::class.java), null, null)
            }
        }
        // Without one-tap pinning, the manual steps become the main instructions.
        if (!pinSupported) findViewById<View>(R.id.manualTitle).visibility = View.GONE

        findViewById<View>(R.id.helloContinue).setOnClickListener {
            show(if (MuhibWidget.hasWidgets(this)) DONE else WIDGET)
        }
        findViewById<View>(R.id.langToggle).setOnClickListener {
            Lang.set(this, if (Lang.current(this) == Lang.TR) Lang.EN else Lang.TR)
            recreate()
        }
        findViewById<View>(R.id.later).setOnClickListener { finishWelcome(openApp = true) }
        findViewById<View>(R.id.goHome).setOnClickListener { askForNotificationsThen { finishWelcome(openApp = false) } }
        findViewById<View>(R.id.openApp).setOnClickListener { askForNotificationsThen { finishWelcome(openApp = true) } }

        show(savedInstanceState?.getInt(KEY_STEP) ?: HELLO)
    }

    override fun onResume() {
        super.onResume()
        startAmbient()
        // The launcher's "add" sheet sits on top of us; once it closes, check whether the widget landed.
        // The launcher can bind it a moment after, so look again shortly.
        checkPlaced()
        window.decorView.postDelayed({ checkPlaced() }, 600)
        window.decorView.postDelayed({ checkPlaced() }, 1500)
    }

    override fun onPause() {
        super.onPause()
        stopAmbient()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, step)
    }

    @Deprecated("Still the simplest back handling on API 26+")
    override fun onBackPressed() {
        if (step == HELLO) super.onBackPressed() else show(HELLO)
    }

    private fun checkPlaced() {
        if (step == WIDGET && !isFinishing && MuhibWidget.hasWidgets(this)) show(DONE)
    }

    private fun show(next: Int) {
        step = next
        steps.forEachIndexed { i, view -> view.visibility = if (i == next) View.VISIBLE else View.GONE }
        findViewById<View>(R.id.langToggle).visibility = if (next == HELLO) View.VISIBLE else View.GONE
        findViewById<View>(R.id.scroll).scrollTo(0, 0)
        Motion.stagger(Motion.children(steps[next]), delay = 100, step = 130)
        stopAmbient()
        startAmbient()
    }

    private fun startAmbient() {
        if (ambient.isNotEmpty()) return
        when (step) {
            HELLO -> {
                ambient += Motion.breathe(findViewById(R.id.helloGlow))
                ambient += Motion.spin(findViewById(R.id.helloStar))
            }
            WIDGET -> {
                findViewById<View>(R.id.previewCovered).alpha = 1f
                findViewById<View>(R.id.previewRevealed).alpha = 0f
                demo.postDelayed({ playDemo(0) }, 1600)
            }
            DONE -> {
                ambient += Motion.ripple(findViewById(R.id.ring1), delay = 700)
                ambient += Motion.ripple(findViewById(R.id.ring2), delay = 2000)
            }
        }
        ambient.forEach { it.start() }
    }

    private fun stopAmbient() {
        ambient.forEach { it.cancel() }
        ambient.clear()
        demo.removeCallbacksAndMessages(null)
    }

    /** Loops a tiny demo in the preview card: a tap, the cover lifts, then it covers again. */
    private fun playDemo(phase: Int) {
        val covered = findViewById<View>(R.id.previewCovered)
        val revealed = findViewById<View>(R.id.previewRevealed)
        val dot = findViewById<View>(R.id.tapDot)
        val wait = when (phase) {
            0 -> {
                dot.scaleX = 0.5f
                dot.scaleY = 0.5f
                dot.alpha = 0.3f
                dot.animate().scaleX(1.4f).scaleY(1.4f).alpha(0f).setStartDelay(0).setDuration(650).setInterpolator(Motion.ease).start()
                350L
            }
            1 -> {
                covered.animate().alpha(0f).setStartDelay(0).setDuration(600).setInterpolator(Motion.ease).start()
                revealed.translationY = 10f * resources.displayMetrics.density
                revealed.animate().alpha(1f).translationY(0f).setStartDelay(200).setDuration(800).setInterpolator(Motion.ease).start()
                3400L
            }
            else -> {
                revealed.animate().alpha(0f).setStartDelay(0).setDuration(600).start()
                covered.animate().alpha(1f).setStartDelay(300).setDuration(900).start()
                2600L
            }
        }
        demo.postDelayed({ playDemo((phase + 1) % 3) }, wait)
    }

    private var afterPermission: (() -> Unit)? = null

    private fun askForNotificationsThen(next: () -> Unit) {
        Muhib.prefs(this).edit().putBoolean(MainActivity.KEY_ASKED, true).apply()
        if (Build.VERSION.SDK_INT >= 33 && Reminders.quietOn(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            afterPermission = next
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            next()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        afterPermission?.invoke()
        afterPermission = null
    }

    private fun finishWelcome(openApp: Boolean) {
        Muhib.prefs(this).edit().putBoolean(KEY_WELCOMED, true).apply()
        Reminders.schedule(this)
        if (openApp) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        private const val HELLO = 0
        private const val WIDGET = 1
        private const val DONE = 2
        private const val KEY_STEP = "step"
        private const val KEY_WELCOMED = "welcomed"
        private const val SAMPLE_ID = "ayah-93-3"

        fun needed(context: Context) = !Muhib.prefs(context).getBoolean(KEY_WELCOMED, false)
    }
}
