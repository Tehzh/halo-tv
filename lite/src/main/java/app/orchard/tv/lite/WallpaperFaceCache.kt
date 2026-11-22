package app.orchard.tv.lite

import android.graphics.Bitmap
import android.util.LruCache

/** Keeps reopening wallpaper settings cheap without retaining every uploaded photo forever. */
internal class WallpaperFaceCache(bytes: Int) : LruCache<String, Bitmap>(bytes) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.rowBytes * value.height
}
