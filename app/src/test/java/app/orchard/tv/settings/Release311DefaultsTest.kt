package app.orchard.tv.settings

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
        ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false)
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun after() {
        ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false)
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun freshInstallDefaultsMatchReleaseAndDoNotPersistOverUserChoices() {
        val context = RuntimeEnvironment.getApplication()
        AerialSettings.init(context)
        assertEquals(1, AerialSettings.settingsIconVisibility)
        assertTrue(AerialSettings.showLunarDate)
        assertEquals(FocusStyle.ClassicGlassSlide, AerialSettings.focusStyle)
        assertTrue(AerialSettings.autoCleanIdle)
        assertEquals(UiSounds.Soft, AerialSettings.uiSounds)
        assertFalse(context.getSharedPreferences("settings", Context.MODE_PRIVATE).contains("settings_icon_visibility"))
    }
    @Test fun explicitChoicesAndLegacyDisabledSoundSurviveUpgrade() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putInt("settings_icon_visibility", 0).putBoolean("show_lunar_date", false)
            .putString("focus_style", "liquid_slide").putBoolean("auto_clean_idle", false)
            .putBoolean("ui_sounds", false).commit()
        AerialSettings.init(context)
        assertEquals(0, AerialSettings.settingsIconVisibility)
        assertFalse(AerialSettings.showLunarDate)
        assertEquals(FocusStyle.LiquidSlide, AerialSettings.focusStyle)
        assertFalse(AerialSettings.autoCleanIdle)
        assertEquals(UiSounds.Off, AerialSettings.uiSounds)
    }
    @Test fun savedTwoRowLayoutAndSixColumnsSurviveRepeatedStarts() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("one_row_home", false).putInt("home_columns", 6).commit()
        repeat(3) {
            ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false)
            AerialSettings.init(context)
            assertFalse(AerialSettings.oneRowHome)
            assertEquals(6, AerialSettings.homeColumns)
            assertFalse(prefs.getBoolean("one_row_home", true))
        }
    }
}
