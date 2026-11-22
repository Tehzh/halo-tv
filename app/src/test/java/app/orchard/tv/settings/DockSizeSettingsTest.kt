package app.orchard.tv.settings

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DockSizeSettingsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    @Before fun before() { reset(); prefs.edit().clear().commit() }
    @After fun after() { reset(); prefs.edit().clear().commit() }
    private fun reset() { ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false) }

    @Test fun upgradeWithoutPreferenceKeepsDefaultWithoutOverwritingOtherChoices() {
        prefs.edit().putInt("home_columns", 6).putBoolean("compact_dock_corners", true).commit()
        AerialSettings.init(context)
        assertEquals(1, AerialSettings.dockSize)
        assertFalse(prefs.contains("dock_size"))
        assertEquals(6, AerialSettings.homeColumns)
        assertTrue(AerialSettings.compactDockCorners)
    }

    @Test fun sizesPersistAndInvalidRequestsCannotReplaceChosenSize() {
        AerialSettings.init(context)
        for (size in 0..2) {
            AerialSettings.chooseDockSize(size)
            reset(); AerialSettings.init(context)
            assertEquals(size, AerialSettings.dockSize)
            assertEquals(size, prefs.getInt("dock_size", -1))
        }
        AerialSettings.chooseDockSize(-1); AerialSettings.chooseDockSize(99)
        assertEquals(2, AerialSettings.dockSize)
        prefs.edit().putInt("dock_size", 99).commit()
        reset(); AerialSettings.init(context)
        assertEquals(1, AerialSettings.dockSize)
    }
}
