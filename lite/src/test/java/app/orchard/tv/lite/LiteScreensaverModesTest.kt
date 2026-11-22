package app.orchard.tv.lite

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiteScreensaverModesTest {
    @Before @After fun resetWallpaperContext() {
        Wallpapers::class.java.getDeclaredField("app").apply { isAccessible = true }.set(Wallpapers, null)
    }

    @Test fun legacyClockChoiceMigratesAndOnlinePicturesPersist() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("wallpaper", 0)
        prefs.edit().putString("screensaver", "clock").commit()

        Wallpapers.init(app)

        assertEquals(ScreensaverMode.FlipClock, Wallpapers.screensaver)
        assertEquals("flip_clock", prefs.getString("screensaver", null))
        assertFalse(ScreensaverMode.entries.any { it.key == "clock" })
        Wallpapers.chooseScreensaver(ScreensaverMode.OnlinePictures)
        assertEquals("bing_online", prefs.getString("screensaver", null))
        assertEquals(ScreensaverMode.OnlinePictures, ScreensaverMode.of(prefs.getString("screensaver", null)))
    }

    @Test fun onlineSlideshowIgnoresLocalChoiceAndKeepsCachedPicturesOffline() {
        val app = RuntimeEnvironment.getApplication()
        Wallpapers.init(app)
        val fallback = Wallpapers.onlineSaverPictures()
        assertTrue(fallback.isNotEmpty())
        assertTrue(fallback.all { it.kind == WallpaperKind.Builtin })

        File(app.filesDir, "photos/own.jpg").writeText("local fixture")
        File(app.filesDir, "daily/20261121.jpg").writeText("online fixture")
        File(app.filesDir, "daily/index.json").writeText("""[{"date":"20261121","title":"Landscape","caption":""}]""")
        Wallpapers.rebuildList()
        LocalSaverSelection.chooseMode(app, video = false, single = true)
        LocalSaverSelection.choose(app, video = false, id = "photo:own")
        assertEquals(listOf("photo:own"), Wallpapers.saverPictures().map { it.id })
        assertEquals(listOf("daily:20261121"), Wallpapers.onlineSaverPictures().map { it.id })
    }
}
