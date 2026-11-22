package app.orchard.tv.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OnlineWallpaperRefreshTest {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private fun waitFor(condition: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < end) { "Wallpaper work did not finish" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(2)
        }
    }
    private fun <T> main(block: suspend () -> T): T {
        val result = work.async { block() }
        waitFor { result.isCompleted }
        return runBlocking { result.await() }
    }
    private fun settled() = waitFor {
        listOf("backdropJob", "bakedWriteJob").all {
            ReflectionHelpers.getStaticField<Job?>(Wallpapers::class.java, it)?.isActive != true
        }
    }
    private fun image(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }
    private fun isColor(bitmap: Bitmap, green: Boolean): Boolean {
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        return if (green) Color.green(pixel) > Color.blue(pixel) + 30 else Color.blue(pixel) > Color.green(pixel) + 30
    }

    @Test fun actualCacheReplacementRefreshesPixelsAndRejectsStaleOrCancelledWork() {
        val app = RuntimeEnvironment.getApplication()
        // Configure the actual pipeline without init's network/daily-rotation services.
        ReflectionHelpers.setStaticField(Wallpapers::class.java, "app", app)
        ReflectionHelpers.setStaticField(Wallpapers::class.java, "screenW", 96)
        ReflectionHelpers.setStaticField(Wallpapers::class.java, "screenH", 54)
        val file = File(app.filesDir, "online-bing-current.jpg")
        val pending = File(app.filesDir, "online-bing-current.pending")
        val green = image(Color.GREEN); val blue = image(Color.BLUE)
        fun apply(bytes: ByteArray): Boolean = main {
            val request = Wallpapers.beginBingRequest()!!
            try { Wallpapers.applyBingCached(bytes, "test", request) }
            finally { Wallpapers.finishBingRequest(request) }
        }
        try {
            main { Wallpapers.select(Wallpapers.AUTO); Wallpapers.trimMemory() }
            settled()
            assertTrue(apply(green)); settled()
            assertTrue(isColor(Wallpapers.backdrop!!.asAndroidBitmap(), true))
            assertTrue(isColor(Wallpapers.frosted!!.asAndroidBitmap(), true))
            assertTrue(isColor(Wallpapers.thumbnail(Wallpapers.BING_ROTATION)!!, true))
            val old = Wallpapers.backdrop
            val oldKey = ReflectionHelpers.getStaticField<String>(Wallpapers::class.java, "bakedKey")
            val oldVersion = Wallpapers.pictureVersion
            val stamp = file.lastModified()
            assertTrue(main {
                val request = Wallpapers.beginBingRequest()!!
                try { Wallpapers.applyBingCached(blue, "second", request).also { if (it) assertTrue(file.setLastModified(stamp)) } }
                finally { Wallpapers.finishBingRequest(request) }
            })
            settled()
            assertNotSame(old, Wallpapers.backdrop)
            assertTrue(Wallpapers.pictureVersion > oldVersion)
            assertNotEquals(oldKey, ReflectionHelpers.getStaticField<String>(Wallpapers::class.java, "bakedKey"))
            assertTrue(isColor(Wallpapers.backdrop!!.asAndroidBitmap(), false))
            assertTrue(isColor(Wallpapers.frosted!!.asAndroidBitmap(), false))
            assertTrue(isColor(Wallpapers.thumbnail(Wallpapers.BING_ROTATION)!!, false))

            val valid = file.readBytes(); val retained = Wallpapers.backdrop
            assertFalse(apply(byteArrayOf(1, 2, 3))); settled()
            assertArrayEquals(valid, file.readBytes()); assertSame(retained, Wallpapers.backdrop)
            val blockedCredit = File(app.filesDir, "online-bing-credit.pending")
            assertTrue(blockedCredit.mkdir())
            try { assertFalse(apply(green)) } finally { blockedCredit.delete() }
            settled(); assertArrayEquals(valid, file.readBytes()); assertSame(retained, Wallpapers.backdrop)

            val first = main { Wallpapers.beginBingRequest()!! }
            val last = main { Wallpapers.beginBingRequest()!! }
            assertTrue(main { Wallpapers.applyBingCached(green, "last intent", last) }); settled()
            assertFalse(main { Wallpapers.applyBingCached(blue, "late old download", first) })
            main { Wallpapers.finishBingRequest(first); Wallpapers.finishBingRequest(last) }
            settled(); assertTrue(isColor(Wallpapers.backdrop!!.asAndroidBitmap(), true))

            assertTrue(apply(blue)); assertTrue(apply(green)); settled()
            assertTrue(isColor(Wallpapers.backdrop!!.asAndroidBitmap(), true))
            assertTrue(isColor(Wallpapers.frosted!!.asAndroidBitmap(), true))

            val beforeCancel = file.readBytes()
            val cancelRequest = main { Wallpapers.beginBingRequest()!! }
            val cancelled = work.async { try { Wallpapers.applyBingCached(blue, "cancel", cancelRequest) } finally { Wallpapers.finishBingRequest(cancelRequest) } }
            shadowOf(Looper.getMainLooper()).idle()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!pending.exists()) { check(System.nanoTime() < deadline); Thread.sleep(1) }
            cancelled.cancel()
            waitFor { cancelled.isCompleted }; settled()
            assertArrayEquals(beforeCancel, file.readBytes()); assertFalse(pending.exists())

            val leavingRequest = main { Wallpapers.beginBingRequest()!! }
            val leaving = work.async { try { Wallpapers.applyBingCached(blue, "changed during staging", leavingRequest) } finally { Wallpapers.finishBingRequest(leavingRequest) } }
            shadowOf(Looper.getMainLooper()).idle()
            val leavingDeadline = System.nanoTime() + 5_000_000_000L
            while (!pending.exists()) { check(System.nanoTime() < leavingDeadline); Thread.sleep(1) }
            Wallpapers.select(Wallpapers.AUTO)
            waitFor { leaving.isCompleted }
            assertFalse(runBlocking { leaving.await() }); settled()
            assertEquals(Wallpapers.AUTO, Wallpapers.selectedId)
            assertArrayEquals(beforeCancel, file.readBytes())
            assertTrue(apply(green)); settled()

            val stale = main { Wallpapers.beginBingRequest()!! }
            main { Wallpapers.select(Wallpapers.LIVE); Wallpapers.select(Wallpapers.BING_ROTATION) }
            assertFalse(main { Wallpapers.applyBingCached(blue, "wrong selection epoch", stale) })
            main { Wallpapers.finishBingRequest(stale) }; settled()
            assertTrue(apply(blue)); settled()
            assertTrue(isColor(Wallpapers.backdrop!!.asAndroidBitmap(), false))
            // Exercise the rotation API with a deterministic local image, without network/timers.
            val rotationClass = OnlineRotation::class.java
            val wasInitialized = ReflectionHelpers.getStaticField<Boolean>(rotationClass, "initialized")
            val wasGeneration = ReflectionHelpers.getStaticField<Int>(rotationClass, "generation")
            val minutes = ReflectionHelpers.getStaticField<androidx.compose.runtime.MutableIntState>(rotationClass, "bingMinutes\$delegate")
            val oldMinutes = minutes.intValue
            try {
                ReflectionHelpers.setStaticField(rotationClass, "initialized", true)
                ReflectionHelpers.setStaticField(rotationClass, "generation", 77)
                minutes.intValue = 5
                assertTrue(main {
                    val request = Wallpapers.beginBingRequest(explicit = false)!!
                    try { Wallpapers.storeBingRotation(green, "rotation", request, 77) }
                    finally { Wallpapers.finishBingRequest(request) }
                })
                settled(); assertTrue(isColor(Wallpapers.backdrop!!.asAndroidBitmap(), true))
                assertFalse(main {
                    val request = Wallpapers.beginBingRequest(explicit = false)!!
                    try { Wallpapers.storeBingRotation(blue, "stale rotation", request, 76) }
                    finally { Wallpapers.finishBingRequest(request) }
                })
                ReflectionHelpers.setStaticField(rotationClass, "app", app)
                val rotationPrefs = app.getSharedPreferences("online_wallpaper_rotation", 0)
                rotationPrefs.edit().putString("bing_category", "nature").commit()
                OnlineRotation.markBingApplied(Wallpapers.BING_ROTATION, null, "20260101")
                assertEquals("nature", rotationPrefs.getString("bing_category", null))
            } finally {
                ReflectionHelpers.setStaticField(rotationClass, "initialized", wasInitialized)
                ReflectionHelpers.setStaticField(rotationClass, "generation", wasGeneration)
                minutes.intValue = oldMinutes
            }
            main { Wallpapers.select(Wallpapers.AUTO) }; settled()
            assertNull(Wallpapers.backdrop)
            assertTrue(apply(green)); settled()
            assertTrue(isColor(Wallpapers.backdrop!!.asAndroidBitmap(), true))
        } finally { work.cancel(); settled() }
    }

    @Test fun choosingAnotherAerialDropsTheOldPaletteAndIgnoresItsLateSample() {
        val app = RuntimeEnvironment.getApplication()
        ReflectionHelpers.setStaticField(Wallpapers::class.java, "app", app)
        ReflectionHelpers.setStaticField(Wallpapers::class.java, "screenW", 96)
        ReflectionHelpers.setStaticField(Wallpapers::class.java, "screenH", 54)
        fun frame(color: Int) = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            main { Wallpapers.chooseAerial("green") }
            settled()
            main { Wallpapers.useFilmFrost(frame(Color.GREEN), "green") }
            assertTrue(isColor(Wallpapers.selectionFilmFrost!!.asAndroidBitmap(), true))
            main { Wallpapers.chooseAerial("blue") }
            settled()
            assertNull(Wallpapers.selectionFilmFrost)
            main { Wallpapers.useFilmFrost(frame(Color.GREEN), "green") }
            assertNull(Wallpapers.selectionFilmFrost)
            main { Wallpapers.keepClipGlass("blue", frame(Color.BLUE)) { "blue" } }
            val stable = Wallpapers.selectionFilmFrost
            assertTrue(isColor(stable!!.asAndroidBitmap(), false))
            main { Wallpapers.useFilmFrost(frame(Color.GREEN), "blue") }
            assertSame("Subsequent frames must not pulse the palette", stable, Wallpapers.selectionFilmFrost)
        } finally {
            main { Wallpapers.select(Wallpapers.AUTO) }
            settled()
        }
    }
}
