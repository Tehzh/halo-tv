package app.orchard.tv.lite

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiteHomeGuardTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test fun onlyTheDedicatedGuardAuthorizationCounts() {
        assertTrue(LiteHomeGuard.supported())
        assertFalse(LiteHomeGuard.authorized(app))
        val power = ComponentName(app, LitePowerService::class.java).flattenToString()
        val guard = ComponentName(app, LiteHomeGuard::class.java)
        val enabled = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        Settings.Secure.putString(app.contentResolver, enabled, power)
        assertFalse(LiteHomeGuard.authorized(app))
        Settings.Secure.putString(app.contentResolver, enabled, "$power:${guard.flattenToShortString()}")
        assertTrue(LiteHomeGuard.authorized(app))
        assertFalse(LiteHomeGuard.connected())

        val info = app.packageManager.getServiceInfo(guard, 0)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", info.permission)
        assertTrue(info.enabled)
    }

    @Test fun oneHomePressOpensOnlyOnceAndConsumesTheMatchingRelease() {
        val key = GuardHomeKey()
        var launches = 0
        fun dispatch(event: KeyEvent, available: Boolean = true) = key.dispatch(event) {
            launches++
            available
        }
        assertFalse(dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        assertTrue(dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertTrue(dispatch(KeyEvent(1, 2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 1)))
        assertFalse(dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertTrue(dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        assertFalse(dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        assertEquals(1, launches)

        assertFalse(dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), available = false))
        assertFalse(dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        assertEquals(2, launches)
    }

    @Test fun coldBootOnlyRecoversFromARealSystemHomeWindowOnce() {
        val stock = ComponentName("vendor.home", "vendor.home.Launcher")
        val pm = shadowOf(app.packageManager)
        val stockActivity = ActivityInfo().apply {
            packageName = stock.packageName
            name = stock.className
            exported = true
            applicationInfo = ApplicationInfo().apply {
                packageName = stock.packageName
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        }
        pm.addOrUpdateActivity(stockActivity)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val target = ResolveInfo().apply {
            activityInfo = stockActivity
            isDefault = true
            priority = 100
        }
        pm.addResolveInfoForIntent(home, target)
        pm.addResolveInfoForIntent(Intent(home).setPackage(stock.packageName), target)
        assertFalse(LiteHomeGuard.isDefaultHome(app))
        assertTrue(app.packageManager.queryIntentActivities(
            Intent(home).setPackage(stock.packageName), PackageManager.MATCH_DEFAULT_ONLY
        ).any { it.activityInfo.name == stock.className && it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 })
        val controller = Robolectric.buildService(LiteHomeGuard::class.java).create()
        val guard = controller.get()
        fun visible(name: String) = guard.onAccessibilityEvent(
            AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
                packageName = stock.packageName
                className = name
            }
        )
        try {
            LiteHomeGuard::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(guard)
            assertTrue(LiteHomeGuard.connected())
            visible("vendor.home.Settings")
            assertNull(shadowOf(app).nextStartedActivity)
            visible(stock.className)
            val started = shadowOf(app).nextStartedActivity
            assertNotNull(started)
            assertEquals(ComponentName(app, HomeActivity::class.java), started.component)
            assertTrue(started.hasCategory(Intent.CATEGORY_HOME))
            visible(stock.className)
            assertNull(shadowOf(app).nextStartedActivity)
        } finally {
            controller.destroy()
            assertFalse(LiteHomeGuard.connected())
        }
    }

}
