package app.orchard.tv.lite

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class WallpaperFaceCacheTest {
    @Test fun browsingMorePhotosEvictsOldCacheEntriesWithoutRecyclingVisibleCards() {
        val first = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val cache = WallpaperFaceCache(first.rowBytes * first.height * 2)
        cache.put("first", first)
        repeat(20) { cache.put("photo-$it", Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)) }
        assertTrue(cache.size() <= cache.maxSize())
        assertNull(cache.get("first"))
        assertNotNull(cache.get("photo-19"))
        assertFalse(first.isRecycled)
    }
}
