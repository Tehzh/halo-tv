package app.orchard.tv.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockTintTest {
    @Test fun usesColourUnderDockWithoutTakingOnDarknessOrTopOfScene() {
        val image = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.BLUE)
        for (y in 19 until 30) for (x in 0 until 30) image.setPixel(x, y, Color.RED)
        val tint = requireNotNull(dockTintOf(image))
        assertTrue(Color.red(tint) > Color.blue(tint))
        assertTrue(Color.green(tint) >= 174)
        image.eraseColor(Color.BLACK)
        assertEquals(NEUTRAL_DOCK_TINT, dockTintOf(image))
        image.eraseColor(Color.TRANSPARENT)
        assertNull(dockTintOf(image))
    }

    @Test fun suppressesMinorChangesButAllowsDifferentScenes() {
        assertFalse(dockTintChanged(NEUTRAL_DOCK_TINT, Color.rgb(233, 236, 248)))
        assertTrue(dockTintChanged(NEUTRAL_DOCK_TINT, Color.rgb(184, 223, 247)))
    }
}
