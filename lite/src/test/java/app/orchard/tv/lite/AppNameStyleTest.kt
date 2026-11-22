package app.orchard.tv.lite

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
        LiteSettings::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(LiteSettings, null)
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun oldAllNamesPreferenceIsPreserved() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("show_all_names", true).commit()
        LiteSettings.init(context)
        assertEquals(1, LiteSettings.appNameStyle)
        assertTrue(LiteSettings.showAllNames)
        assertFalse(LiteSettings.largeAppTitle)
    }
    @Test fun focusedAndLargeTitleAreIndependentChoicesAndPersist() {
        val context = RuntimeEnvironment.getApplication()
        LiteSettings.init(context)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        LiteSettings.chooseAppNameStyle(2)
        assertTrue(LiteSettings.largeAppTitle)
        assertFalse(LiteSettings.showAllNames)
        assertEquals(2, prefs.getInt("app_name_style", -1))
        LiteSettings.chooseAppNameStyle(0)
        assertFalse(LiteSettings.largeAppTitle)
        assertFalse(LiteSettings.showAllNames)
        assertEquals(0, prefs.getInt("app_name_style", -1))
        LiteSettings.chooseAppNameStyle(99)
        assertEquals(0, LiteSettings.appNameStyle)
        LiteSettings.chooseShowAllNames(true)
        assertEquals(1, LiteSettings.appNameStyle)
    }
}
