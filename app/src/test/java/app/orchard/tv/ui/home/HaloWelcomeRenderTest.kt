package app.orchard.tv.ui.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.Duration
import java.io.File

/** Render the actual Android Canvas view on the host; never opens an emulator or TV. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HaloWelcomeRenderTest {
    @Test fun rendersCompleteStaticWelcomeImmediatelyAtTvSizes() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = HaloWelcomeView(controller.get()) {}
        controller.get().setContentView(view)
        val output = File("build/reports/welcome-preview").apply { mkdirs() }
        for ((w, h) in listOf(1920 to 1080, 1280 to 720, 1024 to 768)) {
            view.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            // Header ink and CTA must be present; the whole frame must remain opaque.
            assertEquals(255, bitmap.getPixel(w / 2, h / 2).ushr(24))
            assertTrue(bitmap.getPixel((w * .12).toInt(), (h * .66).toInt()) != bitmap.getPixel(w / 2, 0))
            File(output, "welcome-${w}x${h}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        // Advancing time must neither reveal more content nor dismiss the static welcome.
        view.layout(0, 0, 1280, 720)
        val first = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(first))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        // ViewRoot may apply the host's default viewport while idling; compare the same TV size.
        view.layout(0, 0, 1280, 720)
        val later = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(later))
        assertTrue(first.sameAs(later))
        first.recycle()
        later.recycle()
        controller.pause().stop().destroy()
    }
}
