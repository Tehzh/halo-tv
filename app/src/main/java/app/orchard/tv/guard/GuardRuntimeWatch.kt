package app.orchard.tv.guard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.orchard.tv.system.DiagLog

/** A service loss is a single recovery opportunity, even if callbacks are duplicated. */
internal object GuardRuntimeWatch {
    private val handler = Handler(Looper.getMainLooper())
    private val checks = longArrayOf(6_000L, 24_000L, 60_000L)
    private const val COOLDOWN_MS = 120_000L
    private var lastStartedAt = Long.MIN_VALUE / 2
    private var deadline = 0L
    private var app: Context? = null
    private var attempt = 0
    private val check = Runnable { next() }

    fun lost(context: Context) {
        if (app != null || HomeGuardService.isRunning || !GuardSetup.runtimeRebindAllowed(context)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastStartedAt in 0 until COOLDOWN_MS) return
        lastStartedAt = now
        deadline = now + checks.last() + 2_000L
        app = context.applicationContext
        attempt = 0
        handler.postDelayed(check, checks.first())
    }

    private fun next() {
        val context = app ?: return
        if (SystemClock.elapsedRealtime() >= deadline || HomeGuardService.isRunning || !GuardSetup.runtimeRebindAllowed(context)) {
            stop()
            return
        }
        attempt++
        if (GuardSetup.rebindIfStalled(context, runtime = true)) {
            DiagLog.i("guard runtime: requested rebind ($attempt/${checks.size})")
        }
        if (attempt == checks.size) stop()
        else handler.postDelayed(check, checks[attempt] - checks[attempt - 1])
    }

    fun stop() {
        handler.removeCallbacks(check)
        app = null
    }
}
