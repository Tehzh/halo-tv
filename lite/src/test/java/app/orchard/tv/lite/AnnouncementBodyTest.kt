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
        val sheet = AnnouncementSheet(context, notice, {})
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

    @Test fun readingAreaUsesAvailableHeightAndReportsTheLastPage() {
        val body = AnnouncementBody(context, "完整的安装和守护说明。".repeat(400))
        var progress = -1
        body.onProgress = { progress = it }
        body.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        body.layout(0, 0, 720, 300)
        assertEquals(300, body.height)
        assertEquals(0, progress)
        while (body.page(1)) { }
        assertEquals(100, progress)
        body.page(-1)
        assertTrue(progress < 100)
    }

    @Test fun fullscreenButtonWorksWithoutBrowserAndPreservesReadingPosition() {
        val sheet = AnnouncementSheet(context,
            RemoteAnnouncement("local", 1, "阅读公告", "完整的 AI 指令。\n".repeat(150),
                detailsUrl = "https://halo-tv.pages.dev/help/update-and-setup.html"), {})
        fun layout() {
            sheet.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            sheet.layout(0, 0, 960, 540)
        }
        fun key(code: Int) {
            sheet.onKey(KeyEvent(KeyEvent.ACTION_DOWN, code))
            sheet.onKey(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        layout()
        val body = sheet.focused as AnnouncementBody
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(body.offset > 0)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(sheet.focused is MenuRow)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        layout()
        assertSame(body, sheet.focused)
        assertTrue(body.offset > 0)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(context.getString(R.string.halo_ann_compact), (sheet.focused as MenuRow).text)
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertSame(body, sheet.focused)
    }
}
