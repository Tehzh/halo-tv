package app.orchard.tv.lite

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConditionalRowTest {
    private class Panel : Sheet(RuntimeEnvironment.getApplication(), {}) {
        val first = MenuRow(context, Glyph.Info, "Settings")
        val optional = MenuRow(context, Glyph.Info, "Notice")
        val last = MenuRow(context, Glyph.Info, "Network")
        var clicks = 0
        init { last.action = { clicks++ }; optional.action = { clicks++ }; row(first); row(optional); row(last); focusFirst() }
        fun showNotice(show: Boolean) = showRow(optional, show)
        fun down() { onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)) }
        fun up() { onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)) }
    }
    @Test fun absentNoticeDoesNotLeaveAnInvisibleRemoteFocusStop() {
        val panel = Panel()
        panel.showNotice(false)
        panel.down(); assertSame(panel.last, panel.focused)
        panel.up(); assertSame(panel.first, panel.focused)
        panel.showNotice(true)
        panel.down(); assertSame(panel.optional, panel.focused)
    }
    @Test fun pointerClickFocusesAndActivatesOnlyVisibleControl() {
        val panel = Panel()
        panel.last.performClick()
        assertSame(panel.last, panel.focused)
        assertEquals(1, panel.clicks)
        panel.showNotice(false)
        panel.optional.performClick()
        assertSame(panel.last, panel.focused)
        assertEquals(1, panel.clicks)
    }
    @Test fun pointerClickCancelsAnyEarlierRemotePress() {
        val panel = Panel()
        panel.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        panel.last.performClick()
        panel.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(1, panel.clicks)
    }
    @Test fun remoteFocusChangeCancelsPendingConfirm() {
        val panel = Panel()
        panel.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        panel.down()
        panel.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertSame(panel.optional, panel.focused)
        assertEquals(0, panel.clicks)
    }
    @Test fun cancelledConfirmDoesNotClick() {
        val panel = Panel()
        panel.down()
        panel.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        val cancelled = KeyEvent.changeFlags(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER), KeyEvent.FLAG_CANCELED)
        panel.onKey(cancelled)
        assertEquals(0, panel.clicks)
    }
    @Test fun withdrawingTheFocusedNoticeMovesFocusToAVisibleRow() {
        val panel = Panel()
        panel.down(); assertSame(panel.optional, panel.focused)
        panel.showNotice(false)
        assertSame(panel.last, panel.focused)
        panel.up(); assertSame(panel.first, panel.focused)
    }
}
