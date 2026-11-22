package app.orchard.tv.lite

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GlassClockOptionsTest {
    @Test fun addedChoicesPersistWithoutChangingTheSecondsSwitch() {
        val context = RuntimeEnvironment.getApplication()
        // Robolectric may reuse the singleton while replacing the Application between tests.
        LiteSettings::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(LiteSettings, null)
        LiteSettings.init(context)
        LiteSettings.chooseFocusStyle(FocusStyle.ClassicGlassSlide)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertEquals("classic_glass_slide", prefs.getString("focus_style", null))
        LiteSettings.chooseFlipClockSeconds(false)
        LiteSettings.chooseFlipClockSecondsStyle(1)
        assertEquals(1, prefs.getInt("flip_clock_seconds_style", -1))
        assertFalse(LiteSettings.flipClockSeconds)
        LiteSettings.chooseFlipClockSecondsStyle(-1)
        LiteSettings.chooseFlipClockSecondsStyle(2)
        assertEquals(1, LiteSettings.flipClockSecondsStyle)
        LiteSettings.chooseFlipClockSeconds(true)
        assertEquals(1, LiteSettings.flipClockSecondsStyle)
    }
}
