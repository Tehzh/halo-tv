package app.orchard.tv.lite

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PressTest {
    @Test fun longPressDoesNotAlsoClickOnRelease() {
        val press = Press()
        var clicks = 0
        var longs = 0
        val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
        val repeat = KeyEvent(0, 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1)
        val up = KeyEvent(0, 2, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0)
        for (event in listOf(down, repeat, up)) press.onKey(event, { clicks++ }, { longs++ })
        assertEquals(1, longs)
        assertEquals(0, clicks)
    }
    @Test fun aDifferentConfirmKeyCannotCompleteThePress() {
        val press = Press()
        var clicks = 0
        press.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER), { clicks++ })
        press.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER), { clicks++ })
        press.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER), { clicks++ })
        assertEquals(0, clicks)
    }
    @Test fun cancelledReleaseAndOrphanReleaseDoNotClick() {
        val press = Press()
        var clicks = 0
        press.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER), { clicks++ })
        press.onKey(KeyEvent.changeFlags(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER), KeyEvent.FLAG_CANCELED), { clicks++ })
        press.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER), { clicks++ })
        assertEquals(0, clicks)
    }
}
