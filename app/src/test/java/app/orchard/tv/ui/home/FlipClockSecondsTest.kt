package app.orchard.tv.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlipClockSecondsTest {
    @Test fun colonBlinksInTheMiddleAndTheSecondsSwitchHidesIt() {
        var second = 30
        val clock = FlipClockView(RuntimeEnvironment.getApplication()) { second }
        clock.layout(0, 0, 960, 540)
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        fun centerPixels(): Int {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            clock.draw(canvas)
            return (470..490).sumOf { x -> (180..320).count { y -> Color.red(bitmap.getPixel(x, y)) > 200 } }
        }
        try {
            clock.setContents("1215", "Date · Weather", true, 1)
            assertTrue(centerPixels() > 0)
            clock.setContents("1215", "Date · Weather", false, 1)
            assertEquals(0, centerPixels())
            second = 31
            clock.setContents("1215", "Date · Weather", true, 1)
            assertEquals(0, centerPixels())
            clock.setContents("1215", "Date · Weather", true, 0)
            assertEquals(0, centerPixels())
        } finally { bitmap.recycle() }
    }

    @Test fun legacyPathsGiveEveryCardInkAtPreviewAndFullScreenSizes() {
        val context = RuntimeEnvironment.getApplication()
        for ((width, height) in listOf(960 to 540, 1920 to 1080)) {
            val view = FlipClockView(context, outlineLargeDigits = true)
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                fun frame(progress: Float) {
                    org.robolectric.util.ReflectionHelpers.setField(view, "progress", progress)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.BLACK)
                    view.draw(canvas)
                    val positions = listOf(0.18f, 0.39f, 0.61f, 0.82f)
                    for (center in positions) {
                        val x = (width * (center - 0.062f)).toInt()
                        val w = (width * 0.124f).toInt()
                        val y = (height * 0.24f).toInt()
                        val h = (height * 0.47f).toInt()
                        val pixels = IntArray(w * h)
                        bitmap.getPixels(pixels, 0, w, x, y, w, h)
                        assertTrue("no glyph at $center for $width x $height (progress $progress)",
                            pixels.count { Color.red(it) > 215 } > 300)
                    }
                }
                view.setContents("2313", "Date · Weather", false)
                frame(1f)
                view.setContents("0000", "Date · Weather", false)
                frame(0.25f)
                frame(0.75f)
                frame(1f)
            } finally { bitmap.recycle() }
        }
    }
}
