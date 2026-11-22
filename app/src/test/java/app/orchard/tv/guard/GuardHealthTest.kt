package app.orchard.tv.guard

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GuardHealthTest {
    private val app = RuntimeEnvironment.getApplication()
    private val me get() = ComponentName(app, HomeGuardService::class.java).flattenToString()
    private val ops get() = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Before fun reset() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, me)
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        shadowOf(ops).setMode("android:access_restricted_settings", Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
    }
    @Test fun oldPackageServiceCannotMakeIndependentEditionAppearEnabled() {
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "app.orchard.tv/app.orchard.tv.guard.HomeGuardService")
        val health = GuardHealth.read(app)
        assertEquals(false, health.configured)
        assertEquals(false, health.enabled)
        assertFalse(health.running)
    }
    @Test fun otherGuardWarningDoesNotIncludeUnrelatedAccessibilityServices() {
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "$me:app.orchard.tv/app.orchard.tv.guard.HomeGuardService:example.reader/.ReadingService")
        assertEquals(listOf("app.orchard.tv"), GuardHealth.read(app).otherGuardPackages)
        assertEquals(true, GuardHealth.read(app).enabled)
    }
    @Test fun retainedListEntryWithoutGlobalAccessibilityIsConfiguredButNotEnabled() {
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val health = GuardHealth.read(app)
        assertEquals(true, health.configured)
        assertEquals(false, health.enabled)
        assertFalse(health.running)
    }
    @Test fun writeSecureSettingsAloneDoesNotClaimRestrictedRecoveryIsAllowed() {
        shadowOf(ops).setMode("android:access_restricted_settings", Process.myUid(), app.packageName, AppOpsManager.MODE_ERRORED)
        val health = GuardHealth.read(app)
        assertTrue(health.secureSettingsGranted)
        assertEquals(true, health.restrictedSettingsBlocked)
        assertFalse(health.canRestore)
        assertFalse(health.running)
    }
    @Test fun unknownRestrictedModeDoesNotClaimItWasGranted() {
        shadowOf(ops).setMode("android:access_restricted_settings", Process.myUid(), app.packageName, AppOpsManager.MODE_DEFAULT)
        assertNull(GuardHealth.read(app).restrictedSettingsBlocked)
    }
}
