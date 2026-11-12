package app.orchard.tv.lite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlipClockViewTest {
    @Test fun shortAndNormalCanvasesRenderWithoutAnInvalidFontRange() {
        val clock = FlipClockView(RuntimeEnvironment.getApplication())
        clock.setContents("0915", "日期 · 农历 · 天气", "30")
        for ((width, height) in listOf(320 to 180, 720 to 400, 800 to 480, 960 to 540, 1280 to 720)) {
            clock.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            clock.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try { clock.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
        }
    }

    @Test fun secondsAndLongInfoCanRedrawTheSameCanvas() {
        val clock = FlipClockView(RuntimeEnvironment.getApplication())
        clock.layout(0, 0, 960, 540)
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            for (seconds in listOf("58", "59", "00")) {
                clock.setContents("1215", "很长的日期与天气信息。".repeat(30), seconds)
                clock.draw(canvas)
            }
            clock.setContents("1215", "短信息")
            clock.draw(canvas)
        } finally { bitmap.recycle() }
    }
}
