package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class GuardEntrySafetyTest {
    private val app = RuntimeEnvironment.getApplication()
    private val marker get() = ComponentName(app, BootReceiver::class.java)

    @Before fun reset() {
        app.getSharedPreferences("system", 0).edit().clear().commit()
        app.getSharedPreferences("halo_identity_migration", 0).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 24) {
            app.createDeviceProtectedStorageContext().getSharedPreferences("halo_identity_migration", 0).edit().clear().commit()
            shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        }
        GuardRecoveryPolicy.setEnabled(app, true)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
    }

    private fun recentStockHome() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(4))
        shadowOf(app.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        GuardSetup.setHomeInFront(app, false)
        GuardSetup.noteStockHomeInFront(app)
    }

    @Test fun stoppedOverRecentStockLauncherStillUsesTheFastRecoveryPath() {
        recentStockHome()
        assertTrue(GuardSetup.startAfterStopOverStock(app))
        assertEquals(app.packageName, shadowOf(app).nextStartedActivity.component?.packageName)
    }

    @Test fun staleHandoffCannotBlockIndependentFastRecovery() {
        recentStockHome()
        app.getSharedPreferences("halo_identity_migration", 0).edit().putBoolean("handoff", true).commit()
        assertTrue(GuardSetup.startAfterStopOverStock(app))
        assertEquals(app.packageName, shadowOf(app).nextStartedActivity.component?.packageName)
    }

    @Test fun fastRecoveryDoesNotReadCredentialStateBeforeUnlock() {
        recentStockHome()
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        assertFalse(GuardSetup.startAfterStopOverStock(app))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun missingService() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(app, HomeGuardService::class.java).flattenToString())
        shadowOf(app.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager)
            .setInstalledAccessibilityServiceList(emptyList())
        app.packageManager.setComponentEnabledSetting(marker, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
    }

    /** Branch coverage on the supported API 23 test runtime, not an Android 5 framework test. */
    private inline fun withLollipopBranch(block: () -> Unit) {
        val actualSdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 21)
        try {
            block()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", actualSdk)
        }
    }

    @Test @Config(sdk = [23])
    fun manualAccessibilityEnableWithoutWssStillRestoresTemporaryRescanOverride() = withLollipopBranch {
        missingService()
        GuardSetup.rescanIfMissing(app)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, app.packageManager.getComponentEnabledSetting(marker))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, app.packageManager.getComponentEnabledSetting(marker))
    }

    @Test @Config(sdk = [23])
    fun rescanDoesNotUndoAnExplicitComponentDisable() = withLollipopBranch {
        missingService()
        GuardSetup.rescanIfMissing(app)
        app.getSharedPreferences("halo_identity_migration", 0).edit().putBoolean("handoff", true).commit()
        app.packageManager.setComponentEnabledSetting(marker, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, app.packageManager.getComponentEnabledSetting(marker))
    }

    @Test @Config(sdk = [23])
    fun staleHandoffCannotBlockIndependentServiceRescan() = withLollipopBranch {
        missingService()
        app.getSharedPreferences("halo_identity_migration", 0).edit().putBoolean("handoff", true).commit()
        GuardSetup.rescanIfMissing(app)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, app.packageManager.getComponentEnabledSetting(marker))
    }
}
