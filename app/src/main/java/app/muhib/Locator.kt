package app.muhib

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper

/**
 * One-shot location, only when asked (opening the app or tapping the widget's city).
 * Satellite GPS comes first: it only listens, so nothing about you is sent anywhere.
 * It never runs in the background, and reuses a recent fix instead of waking the radio.
 */
object Locator {
    val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    /** The outcome of one attempt: a [fresh] fix, or only an older one (or none) if the signal was weak. */
    class Result(val location: Location?, val fresh: Boolean)

    fun granted(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Whether location is switched on for the phone. */
    fun enabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java)
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.getProviders(true).any { it != LocationManager.PASSIVE_PROVIDER }
    }

    @SuppressLint("MissingPermission")
    fun current(context: Context, done: (Result) -> Unit) {
        if (!granted(context)) return done(Result(null, false))
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < FRESH) return done(Result(last, true))

        val provider = when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> return done(Result(last, false))
        }
        val main = Handler(Looper.getMainLooper())
        val cancel = CancellationSignal()
        var finished = false
        lateinit var listener: LocationListener
        fun finish(location: Location?) {
            if (finished) return
            finished = true
            main.removeCallbacksAndMessages(null)
            cancel.cancel()
            if (Build.VERSION.SDK_INT < 30) lm.removeUpdates(listener)
            done(if (location != null) Result(location, true) else Result(last, false))
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = finish(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Needed below Android 10")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        main.postDelayed({ finish(null) }, TIMEOUT)
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, cancel, context.mainExecutor) { finish(it) }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            finish(null)
        }
    }

    private const val FRESH = 10 * 60_000L
    private const val TIMEOUT = 20_000L
}
