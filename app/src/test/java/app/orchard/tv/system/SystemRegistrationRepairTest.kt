package app.orchard.tv.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.UserManager
import android.provider.Settings
import app.orchard.tv.dream.AerialDream
import app.orchard.tv.guard.GuardRecoveryPolicy
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.guard.HomeGuardService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SystemRegistrationRepairTest {
    private val app = RuntimeEnvironment.getApplication()
    private val oldGuard = "app.orchard.tv/.guard.HomeGuardService"
    private val oldListener = "app.orchard.tv/.guard.RevivalListener"
    private val oldDream = "app.orchard.tv/app.orchard.tv.dream.AerialDream"
    private val mine get() = ComponentName(app, AerialDream::class.java).flattenToString()
    private val guard get() = ComponentName(app, HomeGuardService::class.java).flattenToString()
    private fun read(key: String) = Settings.Secure.getString(app.contentResolver, key)
    private fun put(key: String, value: String) { Settings.Secure.putString(app.contentResolver, key, value) }

    @Before fun reset() {
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "shutdownPending", false)
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        app.getSharedPreferences("system", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        put(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        put("enabled_notification_listeners", "")
        put("screensaver_components", oldDream)
        put("screensaver_default_component", "com.vendor/.Dream")
        Settings.System.putString(app.contentResolver, "boot_app_componentname", "")
    }

    @Test fun removedLegacyDreamIsRepairedWithoutEnablingGuardOrTransferringListenerAccess() {
        put(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "vendor.assistant/.Service:$oldGuard:vendor.reader/.Service")
        put("enabled_notification_listeners", "vendor.notifications/.Listener:$oldListener")
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        assertTrue(SystemRegistrationRepair.repair(app))
        assertEquals(mine, read("screensaver_components"))
        assertEquals("vendor.assistant/.Service:vendor.reader/.Service", read(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        assertEquals("vendor.notifications/.Listener", read("enabled_notification_listeners"))
        assertEquals(0, Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0))
        assertFalse(GuardRecoveryPolicy.enabled(app))
        assertFalse(SystemRegistrationRepair.repair(app)) // idempotent
    }

    @Test fun installedAerialAndItsSelectedDreamRemainUntouched() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "app.orchard.tv"
            applicationInfo = ApplicationInfo().apply { packageName = "app.orchard.tv" }
        })
        put(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, oldGuard)
        put("enabled_notification_listeners", oldListener)
        assertFalse(SystemRegistrationRepair.repair(app))
        assertEquals(oldDream, read("screensaver_components"))
        assertEquals(oldGuard, read(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        assertEquals(oldListener, read("enabled_notification_listeners"))
    }

    @Test fun deniedPermissionAndLockedStorageNeverMutateRegistrations() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        assertFalse(SystemRegistrationRepair.repair(app))
        assertEquals(oldDream, read("screensaver_components"))
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        assertFalse(SystemRegistrationRepair.repair(app))
        assertEquals(oldDream, read("screensaver_components"))
    }

    @Test fun systemSaverChoiceAndOtherSelectedDreamsAreRespected() {
        app.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit().putString("screensaver", "system").commit()
        assertFalse(SystemRegistrationRepair.repair(app))
        assertEquals("com.vendor/.Dream", read("screensaver_components"))
        put("screensaver_components", "$oldDream,com.vendor/.OtherDream")
        assertFalse(SystemRegistrationRepair.repair(app))
        assertEquals("$oldDream,com.vendor/.OtherDream", read("screensaver_components"))
    }

    @Test fun unknownLegacyComponentsArePreservedEvenIfPackageIsAbsent() {
        put(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "app.orchard.tv/.SomeOtherService:$guard")
        SystemRegistrationRepair.repair(app)
        assertEquals("app.orchard.tv/.SomeOtherService:$guard", read(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
    }

    @Test fun repairedDreamKeepsOwnershipAcrossFirmwareResetWithoutReenablingOptedOutGuard() {
        GuardRecoveryPolicy.setEnabled(app, false)
        GuardSetup.ensureEnabled(app)
        assertEquals(mine, read("screensaver_components"))
        put("screensaver_components", "com.vendor/.Dream")
        GuardSetup.ensureEnabled(app)
        assertEquals(mine, read("screensaver_components"))
        assertFalse(GuardRecoveryPolicy.enabled(app))
        assertFalse(read(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().contains(guard))
    }

    @Test fun malformedSamePackageGuardIsCorrectedButExplicitOptOutWins() {
        put(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "vendor.reader/.Service:${app.packageName}/.guard.HomeGuardService")
        assertTrue(GuardRecoveryPolicy.enabled(app))
        SystemRegistrationRepair.repair(app)
        assertEquals("vendor.reader/.Service", read(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        GuardRecoveryPolicy.setEnabled(app, false)
        put(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "${app.packageName}/.guard.HomeGuardService")
        assertFalse(GuardRecoveryPolicy.enabled(app))
    }

    @Test fun staleVendorBootEntryRequiresIndependentGuardConsent() {
        val key = "boot_app_componentname"
        Settings.System.putString(app.contentResolver, key, "app.orchard.tv/.MainActivity")
        SystemRegistrationRepair.repair(app)
        assertEquals("app.orchard.tv/.MainActivity", Settings.System.getString(app.contentResolver, key))
        GuardRecoveryPolicy.setEnabled(app, true)
        SystemRegistrationRepair.repair(app)
        assertEquals("${app.packageName}/app.orchard.tv.MainActivity", Settings.System.getString(app.contentResolver, key))
        Settings.System.putString(app.contentResolver, key, "com.vendor/.HDMI")
        SystemRegistrationRepair.repair(app)
        assertEquals("com.vendor/.HDMI", Settings.System.getString(app.contentResolver, key))
    }

    @Test fun dreamRepairPreservesNeverScreensaverPolicy() {
        GuardRecoveryPolicy.setEnabled(app, false)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("screensaver_minutes", 0).commit()
        Settings.Secure.putInt(app.contentResolver, "screensaver_enabled", 1)
        GuardSetup.ensureEnabled(app)
        assertEquals(mine, read("screensaver_components"))
        assertEquals(0, Settings.Secure.getInt(app.contentResolver, "screensaver_enabled", 1))
    }
}
