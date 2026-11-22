package app.orchard.tv.lite

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
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
class OnlineWallpaperRefreshTest {
    private fun color(bitmap: Bitmap, green: Boolean): Boolean {
        val p = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        return if (green) Color.green(p) > Color.blue(p) + 30 else Color.blue(p) > Color.green(p) + 30
    }
    @Test fun sameIdRefreshInvalidatesBakeAndThumbnailAndOldRequestsCannotPublish() {
        val app = RuntimeEnvironment.getApplication()
        Ui.init(app); LiteSettings.init(app); Wallpapers.init(app)
        val green = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val blue = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        fun apply(bitmap: Bitmap): String {
            val request = Wallpapers.beginOnlineRequest()
            val temp = Wallpapers.prepareOnline(bitmap)
            return try { Wallpapers.commitOnline(temp, request)!! }
            finally { temp.delete(); Wallpapers.finishOnlineRequest(request) }
        }
        fun verify(green: Boolean, id: String) {
            val frame = Wallpapers.load()
            assertTrue(color(frame.home, green)); assertTrue(color(frame.frost, green))
            frame.home.recycle(); frame.frost.recycle()
            assertTrue(color(Wallpapers.thumbnail(id)!!, green))
        }
        try {
            val id = apply(green); verify(true, id)
            val prefs = app.getSharedPreferences("wallpaper", 0)
            val oldKey = prefs.getString("baked", null)
            val target = File(app.filesDir, "bing-current.jpg")
            val stamp = target.lastModified()
            assertEquals(id, apply(blue)); assertTrue(target.setLastModified(stamp))
            verify(false, id)
            assertNotEquals(oldKey, prefs.getString("baked", null))

            val old = Wallpapers.beginOnlineRequest()
            val latest = Wallpapers.beginOnlineRequest()
            val lateTemp = Wallpapers.prepareOnline(blue)
            val newTemp = Wallpapers.prepareOnline(green)
            try {
                assertEquals(id, Wallpapers.commitOnline(newTemp, latest))
                assertNull(Wallpapers.commitOnline(lateTemp, old))
            } finally { lateTemp.delete(); newTemp.delete(); Wallpapers.finishOnlineRequest(old); Wallpapers.finishOnlineRequest(latest) }
            verify(true, id)
            val valid = target.readBytes()
            val broken = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { recycle() }
            assertTrue(runCatching { Wallpapers.prepareOnline(broken) }.isFailure)
            assertArrayEquals(valid, target.readBytes())
            val cancelled = Wallpapers.beginOnlineRequest()
            val cancelledTemp = Wallpapers.prepareOnline(blue)
            Wallpapers.finishOnlineRequest(cancelled)
            assertNull(Wallpapers.commitOnline(cancelledTemp, cancelled)); cancelledTemp.delete()
            assertArrayEquals(valid, target.readBytes())
            val aba = Wallpapers.beginOnlineRequest()
            val stale = Wallpapers.prepareOnline(blue)
            Wallpapers.select("grad:hills_dusk"); Wallpapers.select(id)
            assertNull(Wallpapers.commitOnline(stale, aba))
            stale.delete(); Wallpapers.finishOnlineRequest(aba)
            verify(true, id)
            assertNull(Wallpapers.onlinePicture(byteArrayOf(1, 2, 3)))
            assertArrayEquals(valid, target.readBytes())
            Wallpapers.select("grad:hills_dusk")
            assertEquals(id, apply(blue)); verify(false, id)
        } finally { green.recycle(); blue.recycle() }
    }
}
