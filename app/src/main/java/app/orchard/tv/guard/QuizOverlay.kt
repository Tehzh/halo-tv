package app.orchard.tv.guard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import app.orchard.tv.settings.KidsLock
import app.orchard.tv.settings.Restricted
import app.orchard.tv.ui.home.QuizPanel

/** Focusable service window: the video app's task is never replaced by the Home task. */
internal object QuizOverlay {
    private var root: QuizPanel? = null
    private var wm: WindowManager? = null
    val visible get() = root != null

    fun dismiss() {
        root?.let { view -> runCatching { wm?.removeViewImmediate(view) } }
        root = null
        wm = null
    }

    fun show(context: Context, accessibility: Boolean): Boolean {
        if (visible) return true
        if (!accessibility && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return false
        return runCatching {
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val panel = QuizPanel(context, onPassed = { reward ->
                Restricted.completeQuiz(reward)
                dismiss()
                KidsWatch.poke()
            }, onParent = {
                showParentPin {
                    Restricted.turnOff()
                    dismiss()
                    KidsWatch.poke()
                }
            })
            val type = if (accessibility && Build.VERSION.SDK_INT >= 22) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(-1, -1, type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE)
            wm = manager
            root = panel
            manager.addView(panel, params)
            true
        }.getOrElse { dismiss(); false }
    }

    fun quizDue(): Boolean = (Restricted.lock as? KidsLock.Break)?.untilMs == Long.MAX_VALUE
}
