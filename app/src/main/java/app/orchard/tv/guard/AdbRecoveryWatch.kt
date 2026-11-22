package app.orchard.tv.guard

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Existing keep-ADB opt-in only. Independent of whether accessibility has bound at boot. */
internal object AdbRecoveryWatch {
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private val checks = ArrayList<Runnable>()

    fun startSession(context: Context) {
        val application = context.applicationContext
        handler.post {
            if (app != null || !GuardSetup.adbRecoveryAllowed(application)) return@post
            app = application
            listOf(2_000L, 15_000L, 45_000L, 90_000L).forEachIndexed { index, delay ->
                val check = Runnable {
                    if (app !== application) return@Runnable
                    if (!GuardSetup.adbRecoveryAllowed(application)) { stop(); return@Runnable }
                    GuardSetup.ensureAdb(application)
                    if (index == 3) stop()
                }
                checks += check
                handler.postDelayed(check, delay)
            }
        }
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { stop() }; return }
        checks.forEach { handler.removeCallbacks(it) }
        checks.clear()
        app = null
    }
}
