package app.orchard.tv.guard

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import app.orchard.tv.system.DiagLog

/**
 * A bounded recovery observer. Boot, real wake and explicit setup may start a fresh session.
 * Ordinary service callbacks cannot extend it, and recovery always respects explicit opt-out.
 * A successful first bind does not end the window: OEM boot code can erase it seconds later.
 */
internal object GuardBootWatch {
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var observer: ContentObserver? = null
    private var deadline = 0L
    private var repairs = 0
    private val tick = Runnable { checkNow() }
    private val missingTick = Runnable {
        missingPosted = false
        checkNow(missingOnly = true)
    }
    private var missingPosted = false
    private val finish = Runnable { stop() }
    private const val BOOT_WINDOW_MS = 5 * 60_000L
    private const val CHECK_MS = 30_000L
    private const val SETTLE_MS = 6_000L
    private const val MISSING_SETTLE_MS = 1_500L
    private const val MAX_REPAIRS = 3

    /** Explicit lifecycle events only; ordinary ensureEnabled/bind callbacks must use start(). */
    fun startSession(context: Context) {
        val application = context.applicationContext
        handler.post {
            if (app != null || !GuardSetup.recoveryAllowed(application)) return@post
            repairs = 0
            begin(application, SystemClock.elapsedRealtime() + BOOT_WINDOW_MS)
        }
    }

    fun start(context: Context) {
        val application = context.applicationContext
        handler.post {
            if (app != null || repairs >= MAX_REPAIRS || SystemClock.elapsedRealtime() >= BOOT_WINDOW_MS || !GuardSetup.recoveryAllowed(application)) return@post
            begin(application, BOOT_WINDOW_MS)
        }
    }

    private fun begin(application: Context, until: Long) {
        app = application
        deadline = until
        val watch = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (observer !== this) return
                if (!missingPosted && GuardSetup.registered(application) == false) {
                    missingPosted = true
                    handler.postDelayed(missingTick, MISSING_SETTLE_MS)
                }
            }
        }
        try {
            application.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, watch)
            application.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, watch)
            observer = watch
        } catch (e: Exception) {
            runCatching { application.contentResolver.unregisterContentObserver(watch) }
            DiagLog.i("guard boot watch: observer unavailable; bounded checks only")
        }
        handler.postDelayed(tick, SETTLE_MS)
        handler.postDelayed(finish, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
    }

    private fun checkNow(missingOnly: Boolean = false) {
        val context = app ?: return
        if (SystemClock.elapsedRealtime() >= deadline || !GuardSetup.recoveryAllowed(context)) { stop(); return }
        val missing = GuardSetup.registered(context) == false
        if (missingOnly && !missing) return
        if ((missing || !HomeGuardService.isRunning) && !GuardSetup.offInStandbyNow(context) && repairs < MAX_REPAIRS) {
            runCatching {
                // A removed entry can be restored even while Android still reports the old binding.
                // A retained entry gets ordinary bind grace and backoff.
                val enabled = GuardSetup.ensureEnabled(context)
                val rebound = if (enabled || missing) false else GuardSetup.rebindIfStalled(context)
                if (enabled || rebound) {
                    repairs++
                    DiagLog.i("guard boot watch: ${if (missing) "restored missing entry" else "requested binding"} ($repairs/$MAX_REPAIRS)")
                }
            }.onFailure { DiagLog.e("guard boot watch recovery failed", it) }
        }
        if (repairs >= MAX_REPAIRS) { stop(); return }
        if (!missingOnly) {
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, CHECK_MS)
        }
    }

    fun stop() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(missingTick)
        missingPosted = false
        handler.removeCallbacks(finish)
        val context = app
        val watch = observer
        app = null
        observer = null
        if (context != null && watch != null) runCatching { context.contentResolver.unregisterContentObserver(watch) }
    }
}
