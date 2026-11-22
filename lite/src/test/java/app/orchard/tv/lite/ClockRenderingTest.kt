package app.orchard.tv.lite

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClockRenderingTest {
    @Test fun dateAndLunarShareOneLineBelowTimeAtEveryAlignment() {
        val app = RuntimeEnvironment.getApplication()
        Ui.init(app)
        LiteSettings.init(app)
        LiteSettings.chooseShowHomeGreeting(false)
        LiteSettings.chooseShowHomeTime(true)
        LiteSettings.chooseShowHomeDate(true)
        LiteSettings.chooseShowLunarDate(true)
        val clock = HaloClockView(app)
        clock.layout(0, 0, 600, 300)
        val lines = mutableListOf<ClockLine>()
        val canvas = object : Canvas() {
            override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                lines += ClockLine(text, x, x + paint.measureText(text), y)
            }
        }
        for ((position, edge) in listOf(
            0 to { line: ClockLine -> line.left },
            1 to { line: ClockLine -> line.right },
            2 to { line: ClockLine -> (line.left + line.right) / 2f },
        )) {
            clock.alignEnd = position == 1
            clock.alignCenter = position == 2
            lines.clear()
            clock.draw(canvas)
            assertEquals(2, lines.size)
            val (time, date) = lines
            assertTrue(date.text.startsWith(dateText() + " · "))
            assertTrue(date.baseline > time.baseline)
            assertEquals(edge(time), edge(date), 0.1f)
        }
        LiteSettings.chooseShowLunarDate(false)
        lines.clear()
        clock.draw(canvas)
        assertEquals(dateText(), lines.last().text)
    }

    private data class ClockLine(val text: String, val left: Float, val right: Float, val baseline: Float)

    @Test fun drawingAndRefreshNeverQueryActivityManager() {
        val app = RuntimeEnvironment.getApplication()
        Ui.init(app)
        LiteSettings.init(app)
        var reads = 0
        val context = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.ACTIVITY_SERVICE) reads++
                return super.getSystemService(name)
            }
        }
        val before = LiteSettings.showDeviceCapacity
        try {
            LiteSettings.chooseShowDeviceCapacity(true)
            val clock = HaloClockView(context)
            clock.layout(0, 0, 600, 300)
            val bitmap = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
            clock.draw(Canvas(bitmap))
            repeat(3) { clock.refresh(); clock.draw(Canvas(bitmap)) }
            assertEquals(0, reads)
            HaloClockView.readCapacity(context)
            assertEquals(1, reads)
            bitmap.recycle()
        } finally { LiteSettings.chooseShowDeviceCapacity(before) }
    }
}
