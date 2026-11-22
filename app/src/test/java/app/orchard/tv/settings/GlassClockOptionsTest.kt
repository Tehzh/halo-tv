package app.orchard.tv.settings

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GlassClockOptionsTest {
    @Before fun freshSettings() { ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false) }
    @After fun releaseSettings() { ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false) }
    @Test fun legacyAndCurrentFocusRemainSeparateAndSecondsStyleDoesNotEnableSeconds() {
        val context = RuntimeEnvironment.getApplication()
        AerialSettings.init(context)
        AerialSettings.chooseFocusStyle(FocusStyle.ClassicGlassSlide)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertEquals("classic_glass_slide", prefs.getString("focus_style", null))
        assertEquals(FocusStyle.LiquidSlide, FocusStyle.of("liquid_slide"))
        assertTrue(FocusStyle.ClassicGlassSlide.sliding)
        assertFalse(FocusStyle.HighContrast.sliding)
        AerialSettings.chooseFlipClockSeconds(false)
        AerialSettings.chooseFlipClockSecondsStyle(1)
        assertEquals(1, prefs.getInt("flip_clock_seconds_style", -1))
        assertFalse(AerialSettings.flipClockSeconds)
        AerialSettings.chooseFlipClockSecondsStyle(-1)
        AerialSettings.chooseFlipClockSecondsStyle(9)
        assertEquals(1, AerialSettings.flipClockSecondsStyle)
        AerialSettings.chooseFlipClockSeconds(true)
        assertEquals(1, AerialSettings.flipClockSecondsStyle)
    }
}
