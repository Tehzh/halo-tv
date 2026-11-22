package app.orchard.tv.guard

import android.Manifest
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
class GuardRuntimeWatchTest {
    private val app = RuntimeEnvironment.getApplication()
    private val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    private val other = "vendor.reader/.Service"
    private val me = ComponentName(app, HomeGuardService::class.java).flattenToString()
    private val power get() = shadowOf(app.getSystemService(PowerManager::class.java))

    @Before fun prepare() {
        waitMs(360_000)
        GuardRuntimeWatch.stop()
        GuardBootWatch.stop()
        ReflectionHelpers.setStaticField(GuardRuntimeWatch::class.java, "lastStartedAt", Long.MIN_VALUE / 2)
        GuardSetup.consumeRuntimeRebind()
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastEnableRequestAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastRebindAttemptAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(GuardSetup::class.java, "rebindPending").set(false)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        power.setIsInteractive(true)
        GuardRecoveryPolicy.setEnabled(app, true)
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        setEntries("$other:$me")
    }

    @After fun cleanUp() {
        GuardRecoveryPolicy.setEnabled(app, false)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
    }

    @Test fun unbindStartsOneRecoveryAndKeepsOtherServicesAndForeground() {
        val service = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", service)
        service.onUnbind(Intent())
        service.onUnbind(Intent())
        waitMs(5_999)
        assertEquals("$other:$me", entries())
        waitMs(1)
        assertEquals(other, entries())
        // A same-process repair must not count as a wake and run the old unknown-front path.
        assertTrue(GuardSetup.consumeRuntimeRebind())
        assertFalse(GuardSetup.consumeRuntimeRebind())
        setEntries("$other:vendor.second/.Reader")
        waitMs(1_500)
        assertEquals("$other:vendor.second/.Reader:$me", entries())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun systemRebindBeforeFirstCheckCancelsRepair() {
        GuardRuntimeWatch.lost(app)
        assertEquals(app, ReflectionHelpers.getStaticField<android.content.Context?>(GuardRuntimeWatch::class.java, "app"))
        val replacement = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", replacement)
        waitMs(60_000)
        assertEquals("$other:$me", entries())
        assertEquals(0, ReflectionHelpers.getStaticField<Int>(GuardRuntimeWatch::class.java, "attempt"))
    }

    @Test fun threeChecksAndCooldownLimitRepeatedLoss() {
        GuardRuntimeWatch.lost(app)
        waitMs(65_000)
        assertEquals(3, ReflectionHelpers.getStaticField<Int>(GuardRuntimeWatch::class.java, "attempt"))
        assertNull(ReflectionHelpers.getStaticField<android.content.Context?>(GuardRuntimeWatch::class.java, "app"))
        GuardRuntimeWatch.lost(app)
        waitMs(30_000)
        assertEquals(3, ReflectionHelpers.getStaticField<Int>(GuardRuntimeWatch::class.java, "attempt"))
        assertEquals("$other:$me", entries())
    }

    @Test fun systemOffOrRemovalIsNeverReenabledByRuntimeWatch() {
        GuardRuntimeWatch.lost(app)
        setEntries(other)
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        waitMs(60_000)
        assertEquals(other, entries())
        assertEquals(0, Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    @Test fun standbyBeforeFirstCheckCancelsBackoff() {
        GuardRuntimeWatch.lost(app)
        power.setIsInteractive(false)
        waitMs(65_000)
        assertEquals("$other:$me", entries())
        assertNull(ReflectionHelpers.getStaticField<android.content.Context?>(GuardRuntimeWatch::class.java, "app"))
    }

    @Test fun permissionRevokedOrExplicitDisableDuringRebindDoesNotReaddGuard() {
        GuardRuntimeWatch.lost(app)
        waitMs(6_000)
        assertEquals(other, entries())
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        GuardRecoveryPolicy.setEnabled(app, false)
        waitMs(65_000)
        assertEquals(other, entries())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun entries() = Settings.Secure.getString(app.contentResolver, key)
    private fun setEntries(value: String) { Settings.Secure.putString(app.contentResolver, key, value) }
    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
}
