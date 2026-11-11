package app.muhib

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Brings the location up to date, off the main thread. Everything stays on the phone. */
object Refresh {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val MOVED_KM = 3.0

    /**
     * Finds where we are once and names the town from the built-in list.
     * [located] runs as soon as the attempt ends; [done] gets whether a fresh fix came in.
     */
    fun here(context: Context, located: () -> Unit = {}, done: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        Locator.current(context) { result ->
            located()
            io.execute {
                val loc = result.location
                if (loc != null) {
                    val old = Places.here(app)
                    val moved = old == null || old.name.isEmpty() ||
                        Qibla.distanceKm(old.lat, old.lng, loc.latitude, loc.longitude) > MOVED_KM
                    // A few metres of drift keeps the old point, but the fix's accuracy and time are always fresh.
                    val base = if (moved) {
                        val city = Cities.nearest(app, loc.latitude, loc.longitude)
                        Place(Places.HERE, city?.name ?: old?.name.orEmpty(), "", loc.latitude, loc.longitude, city?.tz.orEmpty())
                    } else if (old!!.tz.isEmpty()) old.copy(tz = Cities.nearest(app, old.lat, old.lng)?.tz.orEmpty()) else old
                    Places.setHere(app, base.copy(accuracy = loc.accuracy, fixedAt = loc.time))
                }
                main.post {
                    Widgets.refreshAll(app)
                    done(result.fresh)
                }
            }
        }
    }

    fun run(task: () -> Unit) = io.execute(task)
    fun post(task: () -> Unit) = main.post(task)
}
