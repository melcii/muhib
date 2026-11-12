package app.muhib

import android.content.Context
import android.content.Intent

/**
 * Covered: a blurred card that says "Tap this when you need it the most".
 * First tap reveals the reminder in place. Second tap opens the app to read more.
 * Drawing lives in [Widgets], shared with the combined widget.
 */
class MuhibWidget : BaseWidget() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REVEAL) {
            Muhib.reveal(context)
            Widgets.refreshAll(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_REVEAL = "app.muhib.action.REVEAL"

        fun hasWidgets(context: Context) = Widgets.ids(context, Widgets.REMINDER).isNotEmpty() ||
            Widgets.ids(context, Widgets.COMBINED).isNotEmpty()
    }
}
