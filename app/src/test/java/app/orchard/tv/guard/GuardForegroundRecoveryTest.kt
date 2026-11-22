package app.orchard.tv.guard

import android.Manifest
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import app.orchard.tv.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowUsageStatsManager.EventBuilder
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class GuardForegroundRecoveryTest {
    private val app = RuntimeEnvironment.getApplication()
    private val controller = Robolectric.buildService(HomeGuardService::class.java)
    private val service = controller.get()
    private val stock = "vendor.launcher" to "vendor.launcher.Home"
    private val player = "com.tcl.tv" to "com.tcl.tv.Hdmi"
    private val video = "video.app" to "video.app.Player"
    private val power get() = shadowOf(app.getSystemService(PowerManager::class.java))

    @Before fun prepare() {
        waitMs(240_000)
        // Run the worker and result handlers deterministically on the test's virtual clock.
        ReflectionHelpers.setField(service, "usageWorker\$delegate", lazy { Handler(Looper.getMainLooper()) })
        power.setIsInteractive(true)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        accessToUsage(true)
        GuardRecoveryPolicy.setEnabled(app, true)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", service)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "aerialResumed", false)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "allowStockUntil", 0L)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "silentHomeRebound", false)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastEnableRequestAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.setStaticField(GuardSetup::class.java, "lastRebindAttemptAt", Long.MIN_VALUE / 2)
        ReflectionHelpers.getStaticField<java.util.concurrent.atomic.AtomicBoolean>(GuardSetup::class.java, "rebindPending").set(false)
        GuardSetup.consumeRuntimeRebind()
        ReflectionHelpers.setField(service, "stockHomes", mapOf(stock.first to setOf(stock.second), player.first to setOf(player.second)))
    }

    @After fun cleanUp() {
        GuardRecoveryPolicy.setEnabled(app, false)
        controller.destroy()
    }

    @Test fun lateBootFindsStockHomeEvenWhenTheLastWindowWasHaloAndRepeatedBootIsIgnored() {
        val now = System.currentTimeMillis()
        ReflectionHelpers.setField(service, "lastWindow", app.packageName to MainActivity::class.java.name)
        ReflectionHelpers.setField(service, "lastActivityAtWall", now - 1_000L)
        event(stock, now - 250L)
        assertTrue(HomeGuardService.onBootCompleted())
        assertTrue(HomeGuardService.onBootCompleted())
        waitMs(1_500)
        assertHomeOpened()
        assertTrue(HomeGuardService.onBootCompleted())
        waitMs(1_500)
        assertNoHomeOpened()
    }

    @Test fun lateBootDoesNotOpenHomeOverAPlayerThatAlsoDeclaresHome() {
        event(stock, System.currentTimeMillis() - 500L)
        event(player, System.currentTimeMillis() - 50L)
        assertTrue(HomeGuardService.onBootCompleted())
        waitMs(46_000)
        assertNoHomeOpened()
    }

    @Test fun bootCallbackStopsWhenScreenLocksDuringSettle() {
        event(stock, System.currentTimeMillis() - 50L)
        assertTrue(HomeGuardService.onBootCompleted())
        waitMs(100)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        waitMs(46_000)
        assertNoHomeOpened()
    }

    @Test fun homeFindsAStockWindowDeliveredLateWithoutAnAccessibilityEvent() {
        pressHome()
        waitMs(1_900)
        event(stock, System.currentTimeMillis() - 10L)
        waitMs(800)
        assertHomeOpened()
        waitMs(5_000)
        assertNoHomeOpened()
    }

    @Test fun repeatedMissingHomeEventsRebindOnlyHaloAfterReturningToHome() {
        val own = ComponentName(app, HomeGuardService::class.java).flattenToString()
        val other = "vendor.reader/.Service"
        val roster = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(app.contentResolver, roster, "$other:$own")
        fun confirmHomeWithUsageOnly() {
            pressHome()
            waitMs(250)
            event(stock, System.currentTimeMillis() - 10L)
            waitMs(200)
            assertHomeOpened()
            HomeGuardService.onAerialResumed(app)
        }
        confirmHomeWithUsageOnly()
        waitMs(300)
        assertEquals("$other:$own", Settings.Secure.getString(app.contentResolver, roster))
        HomeGuardService.onAerialPaused()
        waitMs(1_000)
        confirmHomeWithUsageOnly()
        waitMs(299)
        assertEquals("$other:$own", Settings.Secure.getString(app.contentResolver, roster))
        waitMs(1)
        assertEquals(other, Settings.Secure.getString(app.contentResolver, roster))
        waitMs(1_500)
        assertEquals("$other:$own", Settings.Secure.getString(app.contentResolver, roster))
        assertEquals(1, Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED))
        assertTrue(ReflectionHelpers.getStaticField(HomeGuardService::class.java, "silentHomeRebound"))
    }

    @Test fun jmgoHomeCanBeConfirmedButItsPlayerCannot() {
        val check = ReflectionHelpers.getField<Runnable>(service, "confirmedRebind")
        val jmgoHome = "com.jmgo.tv" to "com.jmgo.tv.MainActivity"
        val jmgoPlayer = "com.jmgo.tv" to "com.jmgo.tv.AmlogicTvActivity"
        ReflectionHelpers.setField(service, "stockHomes", mapOf(jmgoHome.first to setOf(jmgoHome.second, jmgoPlayer.second)))
        event(jmgoHome, System.currentTimeMillis() - 150)
        event(jmgoPlayer, System.currentTimeMillis() - 50)
        check.run()
        waitMs(0)
        assertNoHomeOpened()

        waitMs(500)
        event(jmgoHome, System.currentTimeMillis() - 50)
        check.run()
        waitMs(0)
        assertHomeOpened()
    }

    @Test fun homeIgnoresUsageEvidenceFromBeforeThePress() {
        event(stock, System.currentTimeMillis() - 500L)
        pressHome()
        waitMs(10_000)
        assertNoHomeOpened()
    }

    @Test fun homePreservesNewerVideoActivityAndDoesNotRetryAfterDeadline() {
        pressHome()
        waitMs(250)
        event(stock, System.currentTimeMillis() - 10L)
        ReflectionHelpers.setField(service, "lastWindow", video)
        ReflectionHelpers.setField(service, "lastActivityAtWall", System.currentTimeMillis())
        waitMs(3_200)
        event(stock, System.currentTimeMillis() - 10L)
        waitMs(5_000)
        assertNoHomeOpened()
    }

    @Test fun homeDoesNotActWhenUsageAccessIsMissingOrUserDisablesRecoveryDuringDelay() {
        accessToUsage(false)
        pressHome()
        waitMs(200)
        event(stock, System.currentTimeMillis() - 10L)
        waitMs(3_000)
        assertNoHomeOpened()

        accessToUsage(true)
        pressHome()
        waitMs(200)
        event(stock, System.currentTimeMillis() - 10L)
        GuardRecoveryPolicy.setEnabled(app, false)
        waitMs(3_000)
        assertNoHomeOpened()
    }

    @Test fun anOlderHomeCallbackCannotAnswerAfterAnAdditionalPress() {
        pressHome()
        val old = ReflectionHelpers.getField<Int>(service, "homeCheckGeneration")
        waitMs(250)
        pressHome()
        val front = UsageFront.Front(stock.first, stock.second, System.currentTimeMillis() - 20, false)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "answerHomePress",
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, old),
            ReflectionHelpers.ClassParameter.from(Long::class.javaPrimitiveType, front.at - 100L),
            ReflectionHelpers.ClassParameter.from(UsageFront.Front::class.java, front))
        assertNoHomeOpened()
    }

    @Test fun newProcessRequiresFreshExactStockAndNoOpenStockVisit() {
        val check = ReflectionHelpers.getField<Runnable>(service, "confirmedRebind")
        event(stock, System.currentTimeMillis() - 31_000L)
        check.run()
        waitMs(0)
        assertNoHomeOpened()

        event(stock.first to "vendor.launcher.Settings", System.currentTimeMillis() - 200L)
        check.run()
        waitMs(0)
        assertNoHomeOpened()

        event(stock, System.currentTimeMillis() - 25L)
        GuardSetup.noteStockVisit(app, SystemClock.elapsedRealtime() + 1_000L)
        check.run()
        waitMs(0)
        assertNoHomeOpened()
        waitMs(1_001)
        check.run()
        waitMs(0)
        assertHomeOpened()
    }

    @Test fun queuedCallbackCannotOpenHomeAfterReplacementBinding() {
        event(stock, System.currentTimeMillis() - 50L)
        val oldCheck = ReflectionHelpers.getField<Runnable>(service, "confirmedRebind")
        service.onUnbind(Intent())
        val replacement = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", replacement)
        oldCheck.run()
        assertNoHomeOpened()
    }

    @Test fun rebindPolicyRequiresCompatibleAndroidPostBootNewProcessAndNoUpdate() {
        fun eligible(sdk: Int = 29, elapsed: Long = 181_000L, age: Long = 20_000L, updating: Boolean = false) =
            GuardForegroundRecovery.recentProcessRebind(sdk, elapsed, 180_000L, age, updating)
        assertTrue(eligible())
        assertFalse(eligible(sdk = 23))
        assertFalse(eligible(elapsed = 179_999L))
        assertFalse(eligible(age = 60_000L))
        assertFalse(eligible(age = -1L))
        assertFalse(eligible(updating = true))
    }

    @Test fun closedUnknownAndFutureUsageAreNotForegroundEvidence() {
        val now = 200_000L
        fun pick(front: UsageFront.Front?) = GuardForegroundRecovery.stock(front, now, now - 30_000L) { pkg, cls -> (pkg to cls) == stock }
        assertNull(pick(null))
        assertNull(pick(UsageFront.Front(stock.first, stock.second, now - 100, true)))
        assertNull(pick(UsageFront.Front(stock.first, stock.second, now + 1, false)))
        assertEquals(stock, pick(UsageFront.Front(stock.first, stock.second, now - 100, false)))
    }

    private fun pressHome() {
        ReflectionHelpers.setField(service, "lastWindow", video)
        val receiver = ReflectionHelpers.getField<android.content.BroadcastReceiver>(service, "homeKey")
        receiver.onReceive(service, Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS).putExtra("reason", "homekey"))
    }

    private fun accessToUsage(allowed: Boolean) {
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), app.packageName, if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED)
    }

    private fun event(activity: Pair<String, String>, at: Long) {
        shadowOf(app.getSystemService(UsageStatsManager::class.java)).addEvent(EventBuilder.buildEvent()
            .setPackage(activity.first).setClass(activity.second).setTimeStamp(at)
            .setEventType(UsageFront.MOVE_TO_FOREGROUND).build())
    }

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun assertNoHomeOpened() = assertNull(shadowOf(app).nextStartedActivity)
    private fun assertHomeOpened() {
        val intent = shadowOf(app).nextStartedActivity
        assertNotNull(intent)
        assertEquals(ComponentName(app, MainActivity::class.java), intent.component)
    }
}
