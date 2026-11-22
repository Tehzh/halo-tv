package app.orchard.tv.settings

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppNameStyleTest {
    @Before fun resetInitialization() {
        AerialSettings::class.java.getDeclaredField("started").apply { isAccessible = true }.set(AerialSettings, false)
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun oldAllNamesPreferenceIsPreserved() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("show_all_names", true).commit()
        AerialSettings.init(context)
        assertEquals(1, AerialSettings.appNameStyle)
        assertTrue(AerialSettings.showAllNames)
        assertFalse(AerialSettings.largeAppTitle)
    }
    @Test fun focusedAndLargeTitleAreIndependentChoicesAndPersist() {
        val context = RuntimeEnvironment.getApplication()
        AerialSettings.init(context)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        AerialSettings.chooseAppNameStyle(2)
        assertTrue(AerialSettings.largeAppTitle)
        assertFalse(AerialSettings.showAllNames)
        assertEquals(2, prefs.getInt("app_name_style", -1))
        AerialSettings.chooseAppNameStyle(0)
        assertFalse(AerialSettings.largeAppTitle)
        assertFalse(AerialSettings.showAllNames)
        assertEquals(0, prefs.getInt("app_name_style", -1))
        AerialSettings.chooseAppNameStyle(99)
        assertEquals(0, AerialSettings.appNameStyle)
        AerialSettings.chooseShowAllNames(true)
        assertEquals(1, AerialSettings.appNameStyle)
    }
}
