package app.orchard.tv.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImagesOwnershipTest {
    @Test fun frostingAnAlreadySmallVideoFrameNeverRecyclesTheRetainedInput() {
        val frame = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.MAGENTA)
        val first = Images.frost(frame)
        assertFalse(frame.isRecycled)
        assertEquals(Color.MAGENTA, frame.getPixel(0, 0))
        val second = Images.frost(frame)
        assertFalse(frame.isRecycled)
        assertNotSame(frame, first); assertNotSame(frame, second)
        first.recycle(); second.recycle(); frame.recycle()
    }
    @Test fun fullSizePhotoRemainsAvailableAfterItsGlassIsPrepared() {
        val frame = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val frost = Images.frost(frame)
        assertFalse(frame.isRecycled)
        assertEquals(320, frost.width); assertEquals(180, frost.height)
        frost.recycle(); frame.recycle()
    }

    @Test fun portraitFrostFollowsTheWindowAndPreservesTheSource() {
        val frame = Bitmap.createBitmap(180, 320, Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.CYAN)
        val frost = Images.frost(frame)
        assertEquals(180, frost.width); assertEquals(320, frost.height)
        assertNotSame(frame, frost)
        assertFalse(frame.isRecycled)
        assertEquals(Color.CYAN, frame.getPixel(0, 0))
        frost.recycle(); frame.recycle()
    }
}
