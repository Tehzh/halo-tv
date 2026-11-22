package app.orchard.tv.guard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowSystemProperties
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class HomeGuardRuntimeTest {
    private val app = RuntimeEnvironment.getApplication()
    private val controller = Robolectric.buildService(HomeGuardService::class.java)
    private val service = controller.get()
    private val vendorHome = ComponentName("com.vendor.launcher", "com.vendor.launcher.HomeActivity")

    @Before
    fun reset() {
        app.getSharedPreferences("halo_identity_migration", Context.MODE_PRIVATE).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 24) {
            app.createDeviceProtectedStorageContext().getSharedPreferences("halo_identity_migration", Context.MODE_PRIVATE)
                .edit().clear().commit()
            shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        }
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "allowStockUntil", 0L)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "aerialResumed", false)
    }

    @After
    fun cleanup() {
        controller.destroy()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", null)
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "allowStockUntil", 0L)
    }

    private fun isActivity(component: ComponentName): Boolean = ReflectionHelpers.callInstanceMethod(
        service, "isActivity",
        ClassParameter.from(String::class.java, component.packageName),
        ClassParameter.from(String::class.java, component.className),
    )

    private fun rescanHomes() = ReflectionHelpers.callInstanceMethod<Unit>(service, "refreshStockHomes")

    private fun releaseHome(): Boolean = ReflectionHelpers.callInstanceMethod(
        service, "onKeyEvent", ClassParameter.from(KeyEvent::class.java, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)),
    )

    @Test
    fun homeRescanRetriesAnActivityUnavailableDuringThePreviousScan() {
        assertFalse(isActivity(vendorHome))
        shadowOf(app.packageManager).addActivityIfNotPresent(vendorHome).exported = true
        // The same service sees a launcher enabled/installed after its first query.
        rescanHomes()
        assertTrue(isActivity(vendorHome))
    }



    @Test
    fun staleHandoffFlagCannotSuppressIndependentGuardBinding() {
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        app.createDeviceProtectedStorageContext().getSharedPreferences("halo_identity_migration", Context.MODE_PRIVATE)
            .edit().putBoolean("handoff", true).commit()
        ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
        assertTrue(HomeGuardService.isRunning)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun unboundInstanceCannotConsumeHomeWhileAReplacementIsBound() {
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", service)
        service.onUnbind(Intent())
        val replacement = Robolectric.buildService(HomeGuardService::class.java).get()
        ReflectionHelpers.setStaticField(HomeGuardService::class.java, "instance", replacement)
        assertTrue(HomeGuardService.isRunning)
        assertFalse(releaseHome())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun lockedBlackBootPoll() {
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        ShadowSystemProperties.override("init.svc.bootanim", "stopped")
        ReflectionHelpers.setField(service, "unlockPending", true)
        ReflectionHelpers.setField(service, "bootBlackStalls", 1)
        ReflectionHelpers.getField<Runnable>(service, "unlockPoll").run()
        assertFalse(ReflectionHelpers.getField(service, "bootBlackStarted"))
        assertNull(shadowOf(app).nextStartedActivity)
    }



    @Test
    fun lockedBootPollFromAnUnboundServiceCannotStartHome() {
        service.onUnbind(Intent())
        lockedBlackBootPoll()
    }

    private fun showJamdeoPanel(className: String) {
        ReflectionHelpers.setField(service, "stockHomes", mapOf("com.jamdeo.launcher" to setOf("com.jamdeo.launcher.HomeActivity")))
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.jamdeo.launcher"
            this.className = className
        }
        service.onAccessibilityEvent(event)
        event.recycle()
    }

    @Test
    @Config(sdk = [23])
    fun androidSixVidaaHomePanelIsDismissed() {
        showJamdeoPanel("android.widget.FrameLayout")
        assertTrue(shadowOf(service).globalActionsPerformed.contains(AccessibilityService.GLOBAL_ACTION_BACK))
    }

    @Test
    @Config(sdk = [23])
    fun androidSixVidaaDialogsAreNotMistakenForHomePanels() {
        showJamdeoPanel("android.app.AlertDialog")
        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
    }

    @Test
    @Config(sdk = [23])
    fun explicitStockLauncherVisitKeepsVidaaPanelAvailable() {
        HomeGuardService.allowStockLauncher()
        showJamdeoPanel("android.widget.RelativeLayout")
        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
    }

    @Test
    @Config(sdk = [28])
    fun vidaaPanelFallbackDoesNotExpandToNewerAndroidVersions() {
        showJamdeoPanel("android.widget.FrameLayout")
        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
    }
}
