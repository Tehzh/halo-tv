package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
class GuardRebindTest {
    private val app = RuntimeEnvironment.getApplication()
    private val me get() = ComponentName(app, HomeGuardService::class.java).flattenToString()
    private val other = "com.vendor.assistant/.AccessibilityService"
    private fun services() = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':')
    @Before fun reset() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        app.getSharedPreferences("system", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("halo_identity_migration", Context.MODE_PRIVATE).edit().clear().commit()
        GuardRecoveryPolicy.setEnabled(app, true)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastEnableRequestAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastRebindAttemptAt", Long.MIN_VALUE / 2)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$other:$me")
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
    }
    @Test fun stalledRebindPreservesOtherServicesAndMarksRecovery() {
        val since = SystemClock.elapsedRealtime()
        GuardSetup.rebindIfStalled(app)
        assertFalse(services().contains(me))
        assertTrue(GuardSetup.lastRevivedAt >= since)
        // Another app adds a service during the removal/re-add interval.
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$other:com.example/.Reader")
        GuardSetup.ensureEnabled(app) // Must not collapse the required 1.5-second gap.
        assertFalse(services().contains(me))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertTrue(services().contains(other))
        assertTrue(services().contains("com.example/.Reader"))
        assertEquals(1, services().count { it == me })
        assertEquals(1, Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0))
    }
    @Test fun noSecurePermissionNeverRemovesServices() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        GuardSetup.rebindIfStalled(app)
        assertEquals(listOf(other, me), services())
    }
    @Test fun staleHandoffCannotBlockIndependentRebind() {
        GuardSetup.rebindIfStalled(app)
        assertFalse(services().contains(me))
        app.getSharedPreferences("halo_identity_migration", Context.MODE_PRIVATE).edit().putBoolean("handoff", true).commit()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertTrue(services().containsAll(listOf(other, me)))
    }
    @Test fun revokedPermissionDuringPendingRebindDoesNotWriteSettings() {
        GuardSetup.rebindIfStalled(app)
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(listOf(other), services())
    }
    @Test fun staleHandoffCannotBlockIndependentComponentRecovery() {
        GuardSetup.ensureEnabled(app)
        app.getSharedPreferences("halo_identity_migration", Context.MODE_PRIVATE).edit().putBoolean("handoff", true).commit()
        val component = ComponentName(app, HomeGuardService::class.java)
        app.packageManager.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertNotEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, app.packageManager.getComponentEnabledSetting(component))
    }
    @Test fun newlyEnabledServiceGetsTimeToBindBeforeAnotherRecoveryRemovesIt() {
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, other)
        GuardSetup.ensureEnabled(app)
        GuardSetup.rebindIfStalled(app)
        assertTrue(services().contains(me))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        GuardSetup.rebindIfStalled(app)
        assertFalse(services().contains(me))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertTrue(services().contains(me))
    }
    @Test fun concurrentRecoveryEntrypointsDoNotRepeatedlyUnbindSlowService() {
        GuardSetup.rebindIfStalled(app)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        GuardSetup.rebindIfStalled(app)
        assertTrue(services().contains(me))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(13500))
        GuardSetup.rebindIfStalled(app)
        assertFalse(services().contains(me))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertTrue(services().contains(me))
    }
}
