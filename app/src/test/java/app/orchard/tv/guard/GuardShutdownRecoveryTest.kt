package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
class GuardShutdownRecoveryTest {
    private val app = RuntimeEnvironment.getApplication()
    private val other = "com.vendor.reader/.ReaderService"
    private val own get() = ComponentName(app, HomeGuardService::class.java).flattenToString()
    private fun entries() = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':')

    @Before fun prepare() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "shutdownPending", false)
        GuardRecoveryPolicy.setEnabled(app, true)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, other)
    }

    @Test fun recoveryWaitsForScreenOnAfterVendorShutdown() {
        val service = Robolectric.buildService(HomeGuardService::class.java).get()
        GuardBootWatch.start(app)
        shadowOf(Looper.getMainLooper()).idle()
        GuardSetup.stepOutForShutdown(service)
        assertFalse(GuardSetup.recoveryAllowed(app))
        assertFalse(GuardSetup.ensureEnabled(app))
        GuardSetup.rebindIfStalled(app)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(listOf(other), entries())
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(GuardSetup.recoveryAllowed(app))
        assertTrue(entries().containsAll(listOf(other, own)))
        GuardBootWatch.stop()
    }

    @Test fun wakingDoesNotOverrideAnExplicitRecoveryOptOut() {
        GuardSetup.stepOutForShutdown(Robolectric.buildService(HomeGuardService::class.java).get())
        GuardRecoveryPolicy.setEnabled(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(GuardSetup.recoveryAllowed(app))
        assertEquals(listOf(other), entries())
    }

    @Test fun notificationRecoveryCannotOpenHomeDuringShutdown() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(4))
        shadowOf(app.getSystemService(android.os.PowerManager::class.java)).setIsInteractive(true)
        GuardSetup.setHomeInFront(app, false)
        GuardSetup.noteStockHomeInFront(app)
        GuardSetup.stepOutForShutdown(Robolectric.buildService(HomeGuardService::class.java).get())
        assertFalse(GuardSetup.startAfterStopOverStock(app))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun shutdownHoldSurvivesProcessStateBeingLost() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(4))
        GuardSetup.stepOutForShutdown(Robolectric.buildService(HomeGuardService::class.java).get())
        // A firmware force-stop loses the singleton, but not the persisted shutdown marker.
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "shutdownPending", false)
        assertFalse(GuardSetup.recoveryAllowed(app))
        assertFalse(GuardSetup.ensureEnabled(app))
        assertFalse(GuardSetup.rebindIfStalled(app))
        assertFalse(GuardSetup.startAfterStopOverStock(app))
        assertEquals(listOf(other), entries())
    }

    @Test fun cancelledShutdownDoesNotBlockRecoveryForever() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(4))
        GuardSetup.stepOutForShutdown(Robolectric.buildService(HomeGuardService::class.java).get())
        assertFalse(GuardSetup.recoveryAllowed(app))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(121))
        assertTrue(GuardSetup.recoveryAllowed(app))
        GuardSetup.ensureEnabled(app)
        assertTrue(entries().containsAll(listOf(other, own)))
    }

    @Test fun markerFromAnotherBootCannotBlockThisBoot() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(4))
        GuardSetup.stepOutForShutdown(Robolectric.buildService(HomeGuardService::class.java).get())
        app.getSharedPreferences("system", 0).edit().putString("shutdown_announced_boot_id", "previous-boot").commit()
        assertTrue(GuardSetup.recoveryAllowed(app))
    }
}
