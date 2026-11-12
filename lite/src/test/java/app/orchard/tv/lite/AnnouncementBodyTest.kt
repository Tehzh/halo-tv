package app.orchard.tv.lite

import android.view.KeyEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnnouncementBodyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(576, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun longSingleParagraphStartsAtTheTopAndCanBeReadToTheEnd() {
        val body = AnnouncementBody(context, "这是需要完整阅读的公告正文。".repeat(350))
        measure(body)
        assertEquals(0, body.offset)
        assertTrue(body.measuredHeight <= Ui.pxi(180f))
        assertTrue(body.maxOffset > 0)
        var steps = 0
        while (body.page(1)) assertTrue(++steps < 300)
        assertEquals(body.maxOffset, body.offset)
        assertFalse(body.page(1))
        while (body.page(-1)) assertTrue(++steps < 600)
        assertEquals(0, body.offset)
        assertFalse(body.page(-1))
    }

    @Test fun aShortNoticeDoesNotTrapRemoteNavigation() {
        val body = AnnouncementBody(context, "短公告")
        measure(body)
        assertEquals(0, body.maxOffset)
        assertFalse(body.page(1))
        assertFalse(body.page(-1))
    }

    @Test fun sheetKeepsFocusOnTheBodyUntilItsLastPage() {
        val notice = RemoteAnnouncement("reading", 1, "长标题".repeat(33), "长公告需要用遥控器逐页阅读。".repeat(350))
        val sheet = AnnouncementSheet(context, notice, {}, {})
        sheet.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        sheet.layout(0, 0, 960, 540)
        val body = sheet.focused as AnnouncementBody
        assertEquals(0, body.offset)
        fun down() {
            sheet.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
            sheet.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        }
        down()
        assertSame(body, sheet.focused)
        assertTrue(body.offset > 0)
        var steps = 0
        while (sheet.focused === body) {
            down()
            assertTrue(++steps < 300)
        }
        assertEquals(body.maxOffset, body.offset)
    }
}
