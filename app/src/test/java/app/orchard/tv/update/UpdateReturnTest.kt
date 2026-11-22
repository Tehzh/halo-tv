package app.orchard.tv.update

import android.content.Context
import android.os.Looper
import android.os.PowerManager
import app.orchard.tv.BuildConfig
import app.orchard.tv.system.BootOpen
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class UpdateReturnTest {
    private val app = RuntimeEnvironment.getApplication()
    private val p get() = app.getSharedPreferences("halo_update_return", Context.MODE_PRIVATE)
    @Before fun reset() {
        p.edit().clear().commit()
        app.getSharedPreferences("system", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun replaced() {
        p.edit().putInt("from", BuildConfig.VERSION_CODE - 1).putLong("target", BuildConfig.VERSION_CODE.toLong())
            .putLong("at", android.os.SystemClock.elapsedRealtime()).putString("boot", BootOpen.BOOT_ID).commit()
    }
    @Test fun requestedButNotInstalledDoesNotStealHome() {
        UpdateReturn.noteInstall(app, BuildConfig.VERSION_CODE + 1L)
        assertFalse(UpdateReturn.claim(app))
        assertFalse(UpdateReturn.keepHome(app))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun actualReplacementClaimsOnceAndSuppressesBootTargetTemporarily() {
        replaced()
        assertTrue(UpdateReturn.claim(app))
        assertFalse(UpdateReturn.claim(app))
        assertTrue(UpdateReturn.keepHome(app))
        assertFalse(BootOpen.due(app))
        assertFalse(BootOpen.openedWithin(app, 1_000))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertFalse(UpdateReturn.keepHome(app))
    }
    @Test fun aLaterRealBootTargetOpenStillSupportsUpstreamReopenPolicy() {
        replaced()
        assertTrue(UpdateReturn.claim(app))
        assertFalse(BootOpen.openedWithin(app, 1_000))
        BootOpen.noteOpened(app)
        assertTrue(BootOpen.openedWithin(app, 1_000))
    }
    @Test fun expiredOrOtherBootDoesNotReopenHome() {
        replaced()
        p.edit().putString("boot", "different-boot").commit()
        assertFalse(UpdateReturn.claim(app))
        replaced()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(16))
        assertFalse(UpdateReturn.claim(app))
    }
    @Test fun cancelledInstallDoesNotReopenHome() {
        replaced(); UpdateReturn.cancel(app)
        assertFalse(UpdateReturn.claim(app))
    }
    @Test fun confirmationOfReplacementReturnsHomeOnce() {
        replaced()
        shadowOf(app.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        UpdateReturn.returnHome(app)
        assertEquals("app.orchard.tv.MainActivity", shadowOf(app).nextStartedActivity?.component?.className)
        UpdateReturn.returnHome(app)
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun olderReleaseInstallerMarkerSurvivesTheFirstUpgradeIntoThisFix() {
        val at = System.currentTimeMillis()
        app.getSharedPreferences("system", Context.MODE_PRIVATE).edit().putLong("installer_opened_at", at).commit()
        shadowOf(app.packageManager).installPackage(app.packageManager.getPackageInfo(app.packageName, 0).apply { lastUpdateTime = at + 500 })
        assertTrue(UpdateReturn.claim(app))
        assertFalse(UpdateReturn.claim(app))
        assertTrue(UpdateReturn.keepHome(app))
    }
    @Test fun cancelledOlderInstallerIsNotAReplacement() {
        val at = System.currentTimeMillis()
        app.getSharedPreferences("system", Context.MODE_PRIVATE).edit().putLong("installer_opened_at", at).commit()
        shadowOf(app.packageManager).installPackage(app.packageManager.getPackageInfo(app.packageName, 0).apply { lastUpdateTime = at - 500 })
        assertFalse(UpdateReturn.claim(app))
    }
    @Test fun updateDoesNotWakeSleepingTv() {
        replaced()
        shadowOf(app.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)
        UpdateReturn.returnHome(app)
        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(UpdateReturn.claim(app))
    }

    @Test fun staleHandoffCannotBlockReturnAfterIndependentUpdate() {
        replaced()
        shadowOf(app.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        app.getSharedPreferences("halo_identity_migration", Context.MODE_PRIVATE).edit().putBoolean("handoff", true).commit()
        UpdateReturn.returnHome(app)
        assertEquals(app.packageName, shadowOf(app).nextStartedActivity.component?.packageName)
        assertFalse(p.contains("from"))
    }
}
