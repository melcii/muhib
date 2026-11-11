package app.muhib

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Find a city by name in the built-in list (no online search) and add it to the places list. */
class CitySearchActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        Accent.paint(window.decorView)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            findViewById<View>(R.id.page).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        val query = findViewById<EditText>(R.id.query)
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                main.removeCallbacksAndMessages(null)
                main.postDelayed({ search(s.toString().trim()) }, 350)
            }
        })
        query.requestFocus()
        window.decorView.postDelayed({
            getSystemService(InputMethodManager::class.java).showSoftInput(query, 0)
        }, 250)
        Motion.stagger(Motion.children(findViewById(R.id.top)), step = 70)
    }

    private fun search(text: String) {
        val ticket = ++generation
        if (text.length < 2) return show(emptyList(), null)
        Refresh.run {
            val found = Cities.search(this, text).map { Cities.place(this, it) }
            main.post {
                if (ticket != generation || isDestroyed) return@post
                show(found, if (found.isEmpty()) getString(R.string.no_results) else null)
            }
        }
    }

    private fun show(places: List<Place>, message: String?) {
        findViewById<TextView>(R.id.message).apply {
            visibility = if (message == null) View.GONE else View.VISIBLE
            text = message
        }
        val list = findViewById<LinearLayout>(R.id.results)
        list.removeAllViews()
        val inflater = LayoutInflater.from(this)
        places.forEach { place ->
            val r = inflater.inflate(R.layout.row_place, list, false)
            r.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_globe)
            r.findViewById<TextView>(R.id.name).text = place.name
            r.findViewById<TextView>(R.id.detail).text = place.detail
            r.findViewById<ImageView>(R.id.remove).apply {
                setImageResource(R.drawable.ic_plus)
                Accent.tint(this, true)
                setOnClickListener { pick(place) }
            }
            r.setOnClickListener { pick(place) }
            list.addView(r)
        }
        Motion.stagger(Motion.children(list), step = 35, rise = 6f)
    }

    private fun pick(place: Place) {
        Places.add(this, place)
        Places.select(this, place.id)
        finish()
    }
}
