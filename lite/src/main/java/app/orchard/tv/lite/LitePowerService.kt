package app.orchard.tv.lite

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import java.lang.ref.WeakReference
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/** Optional system-power-menu bridge only. No window inspection, Home interception or timers. */
class LitePowerService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = WeakReference(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean {
        if (instance.get() === this) instance.clear()
        return super.onUnbind(intent)
    }
    override fun onDestroy() { if (instance.get() === this) instance.clear(); super.onDestroy() }

    companion object {
        private var instance = WeakReference<LitePowerService>(null)
        fun connected() = instance.get() != null
        fun showMenu(): Boolean = Build.VERSION.SDK_INT >= 21 &&
            instance.get()?.let { runCatching { it.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) }.getOrDefault(false) } == true
    }
}
