package app.orchard.tv.guard

import android.Manifest
import android.content.Context
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import org.junit.After
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
class AdbRecoveryWatchTest {
    private val app = RuntimeEnvironment.getApplication()
    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun enabled() = Settings.Global.getInt(app.contentResolver, Settings.Global.ADB_ENABLED, 0)
    private fun dropped() = Settings.Global.putInt(app.contentResolver, Settings.Global.ADB_ENABLED, 0)
    @Before fun reset() {
        AdbRecoveryWatch.stop()
        app.getSharedPreferences("system", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        // Test settings/recovery, never a socket probe or an adbd restart on the host.
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "adbDanced", true)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "adbDancing", false)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "shutdownPending", false)
        dropped()
    }
    @After fun cleanup() { AdbRecoveryWatch.stop() }
    @Test fun secureGrantDoesNotEnableAdbWithoutExistingOptIn() {
        AdbRecoveryWatch.startSession(app); waitMs(100_000)
        assertEquals(0, enabled()); assertFalse(GuardSetup.keepAdb(app))
    }
    @Test fun savedOptInRecoversEvenWithoutAnEnabledGuard() {
        GuardRecoveryPolicy.setEnabled(app, false)
        GuardSetup.setKeepAdb(app, true)
        assertTrue(app.getSharedPreferences("system", 0).getBoolean("keep_adb", false))
        assertEquals(1, enabled())
        dropped(); waitMs(2100)
        assertEquals(1, enabled()); assertFalse(GuardRecoveryPolicy.enabled(app))
    }
    @Test fun optingOutCancelsAllPendingRecovery() {
        GuardSetup.setKeepAdb(app, true); waitMs(1)
        GuardSetup.setKeepAdb(app, false); dropped(); waitMs(100_000)
        assertEquals(0, enabled())
    }
    @Test fun permissionRevokedDuringStartupPreventsLaterWrites() {
        GuardSetup.setKeepAdb(app, true); waitMs(1)
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        dropped(); waitMs(100_000)
        assertEquals(0, enabled())
    }
    @Test fun shutdownHoldPreventsPendingAdbRecovery() {
        GuardSetup.setKeepAdb(app, true); waitMs(1)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "shutdownPending", true)
        dropped(); waitMs(100_000)
        assertEquals(0, enabled())
    }
    @Test fun lockedBootDoesNotReadOrEnableCredentialOptIn() {
        app.getSharedPreferences("system", 0).edit().putBoolean("keep_adb", true).commit()
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        AdbRecoveryWatch.startSession(app); waitMs(100_000)
        assertEquals(0, enabled())
    }
    @Test fun sessionIsBoundedAndRepeatedStartsDoNotExtendIt() {
        GuardSetup.setKeepAdb(app, true); waitMs(40_000)
        AdbRecoveryWatch.startSession(app); waitMs(51_000)
        dropped(); waitMs(100_000)
        assertEquals(0, enabled())
        assertNull(ReflectionHelpers.getStaticField<Context?>(AdbRecoveryWatch::class.java, "app"))
    }
}
