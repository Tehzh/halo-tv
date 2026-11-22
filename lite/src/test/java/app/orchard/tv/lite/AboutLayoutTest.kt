package app.orchard.tv.lite

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-w960dp-h540dp-land-xhdpi")
class AboutLayoutTest {
    @Test fun phoneCardReturnsWhenLeavingAboutAndFocusRemainsOnSidebar() {
        val context = RuntimeEnvironment.getApplication()
        Ui.init(context); LiteSettings.init(context)
        val panel = Personalize(context, close = {}, hiddenCount = 0, onWallpaper = {}, onLayout = {}, onHidden = {}, onHomeLock = {}, onBing = {}, onAbout = {}, onSaverNow = {}, onPin = {}, initialTab = 6)
        fun find(view: View): LitePhoneCard? {
            if (view is LitePhoneCard) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val card = requireNotNull(find(panel))
        val width = View.MeasureSpec.makeMeasureSpec(Ui.screenW, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(Ui.screenH, View.MeasureSpec.EXACTLY)
        panel.measure(width, height)
        panel.layout(0, 0, Ui.screenW, Ui.screenH)
        assertTrue("About content exceeds viewport", requireNotNull(panel.scroller).measuredHeight <= panel.viewport)
        assertEquals(View.GONE, card.visibility)
        panel.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(5, panel.position.first)
        panel.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(View.GONE, card.visibility)
        assertEquals(6, panel.position.first)
    }
}
