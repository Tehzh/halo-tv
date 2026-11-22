package app.orchard.tv.lite

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.robolectric.util.ReflectionHelpers
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Release311DefaultsTest {
    @Before fun before() {
        ReflectionHelpers.setStaticField(LiteSettings::class.java, "prefs", null)
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun after() {
        ReflectionHelpers.setStaticField(LiteSettings::class.java, "prefs", null)
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun freshInstallDefaultsMatchReleaseAndDoNotPersistOverUserChoices() {
        val context = RuntimeEnvironment.getApplication()
        LiteSettings.init(context)
        assertEquals(1, LiteSettings.settingsIconVisibility)
        assertTrue(LiteSettings.showLunarDate)
        assertEquals(FocusStyle.ClassicGlassSlide, LiteSettings.focusStyle)
        assertTrue(LiteSettings.autoCleanIdle)
        assertEquals(UiSounds.Soft, LiteSettings.uiSounds)
        assertFalse(context.getSharedPreferences("settings", Context.MODE_PRIVATE).contains("settings_icon_visibility"))
    }
    @Test fun explicitChoicesAndLegacyDisabledSoundSurviveUpgrade() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putInt("settings_icon_visibility", 0).putBoolean("show_lunar_date", false)
            .putString("focus_style", "standard").putBoolean("auto_clean_idle", false)
            .putBoolean("ui_sounds", false).commit()
        LiteSettings.init(context)
        assertEquals(0, LiteSettings.settingsIconVisibility)
        assertFalse(LiteSettings.showLunarDate)
        assertEquals(FocusStyle.Standard, LiteSettings.focusStyle)
        assertFalse(LiteSettings.autoCleanIdle)
        assertEquals(UiSounds.Off, LiteSettings.uiSounds)
    }
    @Test fun savedTwoRowLayoutAndSixColumnsSurviveRepeatedStarts() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("one_row_home", false).putInt("home_columns", 6).commit()
        repeat(3) {
            ReflectionHelpers.setStaticField(LiteSettings::class.java, "prefs", null)
            LiteSettings.init(context)
            assertFalse(LiteSettings.oneRowHome)
            assertEquals(6, LiteSettings.homeColumns)
            assertFalse(prefs.getBoolean("one_row_home", true))
        }
    }
}
