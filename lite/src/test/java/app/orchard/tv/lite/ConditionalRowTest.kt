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
        init { row(first); row(optional); row(last); focusFirst() }
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
    @Test fun withdrawingTheFocusedNoticeMovesFocusToAVisibleRow() {
        val panel = Panel()
        panel.down(); assertSame(panel.optional, panel.focused)
        panel.showNotice(false)
        assertSame(panel.last, panel.focused)
        panel.up(); assertSame(panel.first, panel.focused)
    }
}
