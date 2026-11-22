package app.orchard.tv.guard

import android.Manifest
import android.app.ActivityManager
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
class GuardActivationTest {
    private val app = RuntimeEnvironment.getApplication()
    private val key = "enabled_notification_listeners"
    private val other = "example.reader/.Reader"
    private val listener get() = ComponentName(app, RevivalListener::class.java).flattenToString()
    private val guard get() = ComponentName(app, HomeGuardService::class.java).flattenToString()
    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Before fun reset() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        shadowOf(app.getSystemService(ActivityManager::class.java)).setIsLowRamDevice(false)
        app.getSharedPreferences("system", 0).edit().clear().commit()
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "shutdownPending", false)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastEnableRequestAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastRebindAttemptAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(GuardSetup::class.java, "rebindPending").set(false)
        GuardBootWatch.stop()
        GuardRecoveryPolicy.setEnabled(app, true)
        Settings.Secure.putString(app.contentResolver, key, other)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, other)
    }

    @Test @Config(sdk = [25])
    fun explicitlyAuthorizedListenerRepairPreservesOtherAppsAndUsesInstalledIdentity() {
        assertTrue(GuardActivation.allowListener(app))
        assertEquals("$other:$listener", Settings.Secure.getString(app.contentResolver, key))
        assertTrue(GuardActivation.allowListener(app))
        assertEquals("$other:$listener", Settings.Secure.getString(app.contentResolver, key))
        assertTrue(GuardHealth.read(app).revivalAllowed)
        assertFalse(GuardActivation.read(app).listenerConnected)
    }

    @Test fun modernSystemApprovalIsCheckedInsteadOfTrustingAStoredString() {
        assertFalse(GuardActivation.allowListener(app))
        assertEquals("$other:$listener", Settings.Secure.getString(app.contentResolver, key))
        assertFalse(GuardHealth.read(app).revivalAllowed)
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        shadowOf(manager).setNotificationListenerAccessGranted(ComponentName(app, RevivalListener::class.java), true)
        assertTrue(GuardHealth.read(app).revivalAllowed)
        assertFalse(GuardActivation.read(app).listenerConnected)
    }

    @Test fun noPrivilegeOrOptOutNeverChangesNotificationAccess() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        assertFalse(GuardActivation.allowListener(app))
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        GuardRecoveryPolicy.setEnabled(app, false)
        assertFalse(GuardActivation.allowListener(app))
        assertEquals(other, Settings.Secure.getString(app.contentResolver, key))
    }

    @Test fun legacyOrMalformedListenerCannotAppearApproved() {
        Settings.Secure.putString(app.contentResolver, key,
            "app.orchard.tv/app.orchard.tv.guard.RevivalListener:${app.packageName}/${app.packageName}.guard.RevivalListener")
        assertFalse(GuardHealth.read(app).revivalAllowed)
    }

    @Test fun lowRamAndroidNineDoesNotPretendItCanEnableNotificationRevival() {
        shadowOf(app.getSystemService(ActivityManager::class.java)).setIsLowRamDevice(true)
        assertTrue(GuardActivation.read(app).listenerUnsupported)
        assertFalse(GuardActivation.allowListener(app))
        assertEquals(other, Settings.Secure.getString(app.contentResolver, key))
        assertFalse(GuardActivation.needed(GuardActivation.read(app), emptySet()).contains(GuardActivation.Step.Notification))
    }

    @Test fun deniedPermissionIsNotLaunchedAgainWithinGuidedRun() {
        val status = GuardActivation.read(app)
        assertTrue(GuardActivation.needed(status, emptySet()).contains(GuardActivation.Step.Notification))
        assertFalse(GuardActivation.needed(status, setOf(GuardActivation.Step.Notification)).contains(GuardActivation.Step.Notification))
        assertTrue(GuardActivation.needed(status, GuardActivation.Step.entries.toSet()).isEmpty())
    }

    @Test fun bindingGraceDoesNotConsumeRecoveryBudget() {
        GuardBootWatch.startSession(app)
        waitMs(5500)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$other:$guard")
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastEnableRequestAt", android.os.SystemClock.elapsedRealtime())
        // Let the real slow-path tick fire while the guard still has time to bind.
        waitMs(500)
        assertEquals(0, ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs"))
        assertFalse(GuardSetup.rebindIfStalled(app))
        GuardBootWatch.stop()
    }

    @Test fun wakingAfterBootWindowAllowsABoundedFreshRecovery() {
        waitMs(360_000)
        GuardBootWatch.startSession(app)
        waitMs(6500)
        assertTrue(Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).contains(guard))
        waitMs(310_000)
        assertNull(ReflectionHelpers.getStaticField<android.content.Context?>(GuardBootWatch::class.java, "app"))
        val repairs = ReflectionHelpers.getStaticField<Int>(GuardBootWatch::class.java, "repairs")
        assertTrue(repairs in 1..3)
    }

    @Test fun listenerConnectionIsSeparateFromApprovalAndClearsOnDestroy() {
        val controller = Robolectric.buildService(RevivalListener::class.java).create()
        val service = controller.get()
        assertFalse(RevivalListener.isConnected)
        service.onListenerConnected()
        assertTrue(RevivalListener.isConnected)
        service.onListenerDisconnected()
        assertFalse(RevivalListener.isConnected)
        service.onListenerConnected()
        controller.destroy()
        assertFalse(RevivalListener.isConnected)
    }

    @Test fun connectedListenerWakesRecoveryAfterBootWithoutOpeningAnApp() {
        val controller = Robolectric.buildService(RevivalListener::class.java).create()
        waitMs(360_000)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, other)
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        waitMs(6500)
        assertTrue(Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).contains(guard))
        assertNull(shadowOf(app).nextStartedActivity)
        controller.destroy()
    }
}
