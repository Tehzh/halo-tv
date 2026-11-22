package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
class GuardBootWatchTest {
    private val app = RuntimeEnvironment.getApplication()
    private val me get() = ComponentName(app, HomeGuardService::class.java).flattenToString()
    private val other = "vendor.reader/.Service"
    private fun entries() = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    private fun setEntries(s: String) { Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, s) }
    private fun waitMs(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }

    @Before fun setup() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        app.getSharedPreferences("system", 0).edit().clear().commit()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
        GuardBootWatch.stop()
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastEnableRequestAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastRebindAttemptAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(GuardSetup::class.java, "rebindPending").set(false)
        ReflectionHelpers.setStaticField(GuardBootWatch::class.java, "repairs", 0)
        setEntries("$other:$me")
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    @Test fun adoptsPreviouslyEnabledGuardAndKeepsIntentAfterFirmwareDropsEntry() {
        assertTrue(GuardRecoveryPolicy.enabled(app))
        setEntries(other)
        assertTrue(GuardRecoveryPolicy.enabled(app))
        assertTrue(GuardSetup.ensureEnabled(app))
        assertEquals("$other:$me", entries())
    }

    @Test fun securePermissionAloneDoesNotTurnOnUnconfiguredGuard() {
        setEntries(other)
        assertFalse(GuardRecoveryPolicy.enabled(app))
        assertFalse(GuardSetup.ensureEnabled(app))
        assertEquals(other, entries())
    }

    @Test fun explicitOptOutSurvivesExistingEntryAndAllRecoveryEntrypoints() {
        GuardRecoveryPolicy.setEnabled(app, false)
        assertFalse(GuardRecoveryPolicy.enabled(app))
        GuardSetup.rebindIfStalled(app)
        assertEquals("$other:$me", entries())
        setEntries(other)
        GuardSetup.ensureEnabled(app)
        GuardSetup.restartAccessibility(app)
        GuardBootWatch.start(app)
        waitMs(35_000)
        assertEquals(other, entries())
        assertFalse(GuardSetup.startAfterStopOverStock(app))
    }

    @Test fun lateDropAfterSuccessfulBindIsRecoveredWithinBootWindow() {
        val service = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", service)
        GuardBootWatch.start(app)
        waitMs(10_000)
        assertEquals(0, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
        setEntries(other)
        waitMs(7_000)
        assertEquals("$other:$me", entries())
        assertEquals(1, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
    }

    @Test fun missingEntryIsRestoredAfterShortSettleEvenWhileOldBindingIsStillReported() {
        val service = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", service)
        GuardRecoveryPolicy.setEnabled(app, true)
        GuardBootWatch.start(app)
        waitMs(6_500)
        setEntries(other)
        waitMs(1_499)
        assertEquals(other, entries())
        waitMs(1)
        assertEquals("$other:$me", entries())
        assertEquals(1, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
    }

    @Test fun retainedEntryStillGetsTheLongerBindGrace() {
        GuardRecoveryPolicy.setEnabled(app, true)
        GuardBootWatch.start(app)
        waitMs(1_501)
        assertEquals("$other:$me", entries())
        assertEquals(0, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
        waitMs(4_499)
        assertEquals(other, entries())
        waitMs(1_500)
        assertEquals("$other:$me", entries())
    }

    @Test fun temporaryRosterDropAndUserCancellationDoNotTriggerTheFastRepair() {
        val service = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", service)
        GuardRecoveryPolicy.setEnabled(app, true)
        GuardBootWatch.start(app)
        waitMs(6_500)
        setEntries(other)
        waitMs(500)
        setEntries("$other:$me")
        waitMs(1_000)
        assertEquals(0, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))

        setEntries(other)
        waitMs(500)
        GuardRecoveryPolicy.setEnabled(app, false)
        waitMs(2_000)
        assertEquals(other, entries())
        assertEquals(0, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
    }

    @Test fun optOutDuringRebindDoesNotPutGuardBack() {
        GuardRecoveryPolicy.setEnabled(app, true)
        GuardSetup.rebindIfStalled(app)
        assertEquals(other, entries())
        GuardRecoveryPolicy.setEnabled(app, false)
        waitMs(2_000)
        assertEquals(other, entries())
    }

    @Test fun noPermissionNeverRestoresList() {
        GuardRecoveryPolicy.setEnabled(app, true)
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        setEntries(other)
        GuardBootWatch.start(app)
        waitMs(10_000)
        assertEquals(other, entries())
    }

    @Test fun noCredentialPrefsAreReadBeforeUnlock() {
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        val locked = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("Credential storage is locked")
        }
        assertFalse(GuardRecoveryPolicy.enabled(locked))
        GuardRecoveryPolicy.setEnabled(locked, true)
    }

    @Test fun watcherExpiresAndCannotBecomePermanentPolling() {
        val service = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", service)
        GuardBootWatch.start(app)
        waitMs(301_000)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
        setEntries(other)
        GuardBootWatch.start(app)
        waitMs(10_000)
        assertEquals(other, entries())
        assertNull(ReflectionHelpers.getStaticField<Context?>(GuardBootWatch::class.java, "app"))
    }

    @Test fun standbyOptOutIsRespectedDuringBootChecks() {
        GuardRecoveryPolicy.setEnabled(app, true)
        GuardSetup.setOffInStandby(app, true)
        shadowOf(app.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)
        setEntries(other)
        GuardBootWatch.start(app)
        waitMs(35_000)
        assertEquals(other, entries())
        assertEquals(0, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
    }

    @Test fun repeatedBindingFailureIsCappedAtThreeRepairs() {
        GuardRecoveryPolicy.setEnabled(app, true)
        GuardBootWatch.start(app)
        waitMs(301_000)
        assertEquals(3, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
        assertNull(ReflectionHelpers.getStaticField<Context?>(GuardBootWatch::class.java, "app"))
        assertTrue(entries()!!.contains(other))
    }
}
