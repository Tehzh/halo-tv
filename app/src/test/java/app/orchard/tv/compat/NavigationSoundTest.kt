package app.orchard.tv.compat

import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.UiSounds
import app.orchard.tv.system.Sounds
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationSoundTest {
    @Before fun prepare() {
        ReflectionHelpers.getStaticField<SoundPool?>(Sounds::class.java, "pool")?.release()
        ReflectionHelpers.setStaticField(Sounds::class.java, "pool", null)
        ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false)
        AerialSettings.init(RuntimeEnvironment.getApplication())
        AerialSettings.chooseUiSounds(UiSounds.Aerial)
        Sounds.init(RuntimeEnvironment.getApplication())
    }
    @After fun silence() {
        AerialSettings.chooseUiSounds(UiSounds.Off)
        ReflectionHelpers.setStaticField(AerialSettings::class.java, "started", false)
    }

    @Test fun loadingSamplesCannotBePlayedUntilReady() {
        val pool = ReflectionHelpers.getStaticField<SoundPool>(Sounds::class.java, "pool")
        val loaded = ReflectionHelpers.getStaticField<MutableSet<Int>>(Sounds::class.java, "loaded")
        loaded.clear()
        Sounds.select()
        assertFalse(shadowOf(pool).wasResourcePlayed(R.raw.ui_select))
        shadowOf(pool).notifyResourceLoaded(R.raw.ui_select, true)
        Sounds.select()
        assertTrue(shadowOf(pool).wasResourcePlayed(R.raw.ui_select))
    }

    @Test fun offAppliesImmediatelyIncludingChangesFromPhoneSettings() {
        val pool = ReflectionHelpers.getStaticField<SoundPool>(Sounds::class.java, "pool")
        shadowOf(pool).notifyResourceLoaded(R.raw.ui_select, true)
        Sounds.select()
        shadowOf(pool).clearPlayed()
        AerialSettings.chooseUiSounds(UiSounds.Off)
        Sounds.select(); Sounds.step(); Sounds.limit()
        Sounds.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertFalse(shadowOf(pool).wasResourcePlayed(R.raw.ui_select))
        assertFalse(shadowOf(pool).wasResourcePlayed(R.raw.ui_move))
        assertFalse(shadowOf(pool).wasResourcePlayed(R.raw.ui_back))
    }

    @Test fun multipleFocusChangesForOneRemotePressProduceOneSound() {
        val pool = ReflectionHelpers.getStaticField<SoundPool>(Sounds::class.java, "pool")
        shadowOf(pool).notifyResourceLoaded(R.raw.ui_move, true)
        val at = SystemClock.uptimeMillis()
        Sounds.onKey(KeyEvent(at, at, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
        Sounds.focusMoved(); Sounds.focusMoved()
        assertEquals(1, shadowOf(pool).getResourcePlaybacks(R.raw.ui_move).size)
    }
}
