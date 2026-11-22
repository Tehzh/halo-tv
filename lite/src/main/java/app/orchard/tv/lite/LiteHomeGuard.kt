package app.orchard.tv.lite

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** User-authorized Home recovery. Window events only supply the bounded cold-boot fallback. */
class LiteHomeGuard : AccessibilityService() {
    private val homeKey = GuardHomeKey()
    private var bootPending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        bootPending = inBootWindow() && !bootHandled(this)
        instance = this
        listeners.toList().forEach { it() }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = homeKey.dispatch(event) {
        if (isDefaultHome(this)) false else openHome()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!bootPending) return
        if (!inBootWindow() || bootHandled(this)) {
            bootPending = false
            return
        }
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (isDefaultHome(this) || pkg == packageName || !isSystemHomeWindow(pkg, cls)) return

        // Only an actual preinstalled HOME activity may trigger an unattended launch.
        bootPending = false
        noteHomeVisible(this)
        openHome()
    }

    private fun isSystemHomeWindow(pkg: String, cls: String): Boolean = runCatching {
        val windowClass = if (cls.startsWith('.')) pkg + cls else cls
        packageManager.queryIntentActivities(homeIntent().setPackage(pkg), PackageManager.MATCH_DEFAULT_ONLY)
            .any {
                val activity = it.activityInfo
                activity.packageName == pkg && activity.name == windowClass &&
                    activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            }
    }.getOrDefault(false)

    private fun openHome(): Boolean = runCatching {
        startActivity(homeIntent().setClass(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnected()
        super.onDestroy()
    }

    private fun disconnected() {
        if (instance !== this) return
        instance = null
        listeners.toList().forEach { it() }
    }

    companion object {
        private const val BOOT_WINDOW_MS = 90_000L
        private const val BOOT_KEY = "handled_boot_epoch"
        @Volatile private var instance: LiteHomeGuard? = null
        private val listeners = LinkedHashSet<() -> Unit>()

        fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

        fun authorized(context: Context): Boolean {
            if (!supported()) return false
            val service = ComponentName(context, LiteHomeGuard::class.java)
            val enabled = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull()
            return enabled.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == service }
        }

        fun connected() = instance != null
        fun observe(listener: () -> Unit) { listeners.add(listener) }
        fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }

        fun isDefaultHome(context: Context): Boolean = runCatching {
            val resolved = context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == context.packageName
        }.getOrDefault(false)

        fun noteHomeVisible(context: Context) {
            if (!supported() || !inBootWindow() || bootHandled(context)) return
            bootPrefs(context).edit().putLong(BOOT_KEY, bootEpoch()).apply()
        }

        private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        private fun inBootWindow() = SystemClock.elapsedRealtime() < BOOT_WINDOW_MS
        private fun bootEpoch() = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        private fun bootPrefs(context: Context) = context.getSharedPreferences("lite_guard", Context.MODE_PRIVATE)
        private fun bootHandled(context: Context): Boolean {
            val handled = bootPrefs(context).getLong(BOOT_KEY, 0L)
            return handled > 0 && kotlin.math.abs(handled - bootEpoch()) < 15_000
        }
    }
}

/** Preserve the matching key-up even after the Home activity has taken foreground focus. */
internal class GuardHomeKey {
    private var handled = false

    fun dispatch(event: KeyEvent, openHome: () -> Boolean): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) handled = openHome()
                handled
            }
            KeyEvent.ACTION_UP -> handled.also { handled = false }
            else -> false
        }
    }
}
