package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import app.orchard.tv.dream.AerialDream
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SystemScreensaverRestoreTest {
    private val firmware = "com.vendor.tv/.FirmwareDream"
    private val app get() = RuntimeEnvironment.getApplication()
    private val own get() = ComponentName(app, AerialDream::class.java).flattenToString()

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        Settings.Secure.putString(app.contentResolver, "screensaver_default_component", firmware)
    }

    @Test
    fun systemModeRestoresFirmwareAfterNeverAndReturningReappliesNever() {
        val resolver = app.contentResolver
        Settings.Secure.putString(resolver, "screensaver_components", own)
        Settings.Secure.putInt(resolver, "screensaver_enabled", 1)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("screensaver_minutes", 0).apply()
        GuardSetup.setSystemScreensaverOff(app, true)
        assertEquals(0, Settings.Secure.getInt(resolver, "screensaver_enabled"))

        choose("system")
        assertEquals(firmware, Settings.Secure.getString(resolver, "screensaver_components"))
        assertEquals(1, Settings.Secure.getInt(resolver, "screensaver_enabled"))

        choose("photos")
        assertEquals(own, Settings.Secure.getString(resolver, "screensaver_components"))
        assertEquals(0, Settings.Secure.getInt(resolver, "screensaver_enabled"))
    }

    @Test
    fun systemModePreservesUserDisabledThirdPartyScreensaver() {
        val resolver = app.contentResolver
        val selected = "com.other.tv/.Dream"
        Settings.Secure.putString(resolver, "screensaver_components", selected)
        Settings.Secure.putInt(resolver, "screensaver_enabled", 0)

        choose("system")
        assertEquals(selected, Settings.Secure.getString(resolver, "screensaver_components"))
        assertEquals(0, Settings.Secure.getInt(resolver, "screensaver_enabled"))
    }

    private fun choose(mode: String) {
        app.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit().putString("screensaver", mode).apply()
        GuardSetup.screensaverModeChanged(app)
    }
}
