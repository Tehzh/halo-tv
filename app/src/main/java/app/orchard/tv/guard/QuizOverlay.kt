package app.orchard.tv.guard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import app.orchard.tv.settings.KidsLock
import app.orchard.tv.settings.Restricted
import app.orchard.tv.ui.home.QuizPanel

/** Focusable service window: the video app's task is never replaced by the Home task. */
internal object QuizOverlay {
    private var root: QuizPanel? = null
    private var wm: WindowManager? = null
    private var owner: Context? = null
    private var requestedAt = 0L
    private var currentRoute = ""
    private var pausedPlayback: QuizPlayback? = null
    val visible get() = root?.isAttachedToWindow == true

    fun dismiss(onlyOwner: Context? = null) {
        if (onlyOwner != null && owner !== onlyOwner) return
        val view = root
        val manager = wm
        root = null
        wm = null
        owner = null
        currentRoute = ""
        pausedPlayback = null
        view?.let { runCatching { manager?.removeViewImmediate(it) } }
    }

    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 23 ||
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    fun show(context: Context, accessibility: Boolean): String {
        val route = if (accessibility) "accessibility" else "application"
        if (visible) return "attached:$currentRoute"
        if (root != null) {
            if (SystemClock.elapsedRealtime() - requestedAt < 1_000L) return "pending:$currentRoute"
            val expired = currentRoute
            dismiss()
            if (expired == route) return "${route}_overlay_attach_timeout"
        }
        if (accessibility && Build.VERSION.SDK_INT < 22) return "accessibility_overlay_unsupported"
        if (!accessibility && !allowed(context)) return "application_overlay_not_granted"
        return runCatching {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val foreground = KidsWatch.quizForegroundPackage(context)
            val panel = QuizPanel(context, onPassed = { reward ->
                val canResume = Restricted.completeQuiz(reward)
                val playback = pausedPlayback.takeIf { canResume }
                val view = root
                dismiss()
                if (playback != null && view?.isAttachedToWindow == false && GuardHealth.listenerAllowed(context)) {
                    playback.resume(KidsWatch.quizForegroundPackage(context))
                }
                KidsWatch.poke()
            }, onParent = {
                showParentPin {
                    Restricted.turnOff()
                    dismiss()
                    KidsWatch.poke()
                }
            })
            val type = if (accessibility) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(-1, -1, type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE)
            wm = manager
            root = panel
            owner = context
            requestedAt = SystemClock.elapsedRealtime()
            currentRoute = route
            panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    // Try before the first draw, once the quiz window is known to have attached.
                    // A failed overlay must never pause a third-party player.
                    if (root === panel && quizDue() && foreground != null &&
                        foreground == KidsWatch.quizForegroundPackage(context)) {
                        pausedPlayback = QuizPlayback.pause(context, foreground)
                    }
                    KidsWatch.poke()
                }
                override fun onViewDetachedFromWindow(v: View) {
                    if (root === panel) {
                        root = null
                        wm = null
                        owner = null
                        pausedPlayback = null
                        KidsWatch.poke()
                    }
                }
            })
            manager.addView(panel, params)
            if (visible) "attached:$route" else "pending:$route"
        }.getOrElse { error ->
            dismiss()
            "${route}_overlay_${error.javaClass.simpleName}"
        }
    }

    fun quizDue(): Boolean = (Restricted.lock as? KidsLock.Break)?.untilMs == Long.MAX_VALUE
}
