package app.muhib

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/**
 * Covered: a blurred card that says "Tap this when you need it the most".
 * First tap reveals the reminder in place. Second tap opens the app to read more.
 */
class MuhibWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        Reminders.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REVEAL) {
            Muhib.reveal(context)
            refreshAll(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_REVEAL = "app.muhib.action.REVEAL"
        private const val ARABIC_MAX_IN_WIDGET = 110

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, MuhibWidget::class.java))
                .forEach { update(context, manager, it) }
        }

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, MuhibWidget::class.java)).isNotEmpty()

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val reminder = Muhib.current(context)
            val revealed = Muhib.isRevealed(context)
            val views = RemoteViews(context.packageName, R.layout.widget_muhib)

            views.setDisplayedChild(R.id.flipper, if (revealed) 1 else 0)
            if (revealed) {
                // Long Arabic passages stay in the app; the widget shows the meaning.
                val arabic = reminder.arabic?.takeIf { it.length <= ARABIC_MAX_IN_WIDGET }
                views.setTextViewText(R.id.arabic, arabic.orEmpty())
                views.setViewVisibility(R.id.arabic, if (arabic == null) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.text, reminder.text)
                views.setTextViewText(R.id.source, reminder.source)
                views.setOnClickPendingIntent(android.R.id.background, openApp(context))
            } else {
                views.setTextViewText(R.id.prompt, Lang.wrap(context).getString(R.string.prompt))
                views.setImageViewBitmap(R.id.blur, Blur.render(reminder))
                views.setOnClickPendingIntent(android.R.id.background, reveal(context))
            }
            manager.updateAppWidget(id, views)
        }

        private fun reveal(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, MuhibWidget::class.java).setAction(ACTION_REVEAL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
