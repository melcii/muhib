package app.muhib

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * An invisible step behind the widget's city: location may only be read while the app is in
 * front, so this takes one fix, hands off, and closes.
 */
class RefreshActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Locator.granted(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        Widgets.showLocating(this)
        Refresh.here(this, located = { finish() })
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
