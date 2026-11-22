package app.orchard.tv.ui.home

import app.orchard.tv.wallpaper.ScreensaverMode
import app.orchard.tv.wallpaper.Wallpapers
import org.junit.Assert.*
import org.junit.Test

class WallpaperTransitionsTest {
    @Test fun firstVisitStillHonorsIdleOnlyPlayback() {
        val policy = WallpaperResumePolicy()
        assertFalse(policy.movingChanged(true))
        policy.movingChanged(false)
        assertFalse(policy.movingChanged(true))
    }

    @Test fun returningToRenderedVideoSkipsIdleWaitOnlyOnce() {
        val policy = WallpaperResumePolicy()
        policy.movingChanged(true)
        policy.frameRendered()
        assertFalse(policy.movingChanged(false))
        assertTrue(policy.movingChanged(true))
        assertFalse(policy.movingChanged(true))
        policy.movingChanged(false)
        assertTrue(policy.movingChanged(true))
    }

    @Test fun onlyNonVideoSaversRetainTheWallpaperDecoder() {
        for (mode in ScreensaverMode.entries) {
            assertEquals(mode.toString(), mode in listOf(ScreensaverMode.Photos,
                ScreensaverMode.Bing, ScreensaverMode.Clock, ScreensaverMode.FlipClock),
                saverRetainsWallpaper(mode))
        }
    }

    @Test fun identicalSingleSelectionCanKeepItsExistingSurface() {
        assertTrue(localSaverCarriesWallpaper(Wallpapers.VIDEO_PREFIX + "a", listOf("a", "b"), true, "a"))
        assertFalse(localSaverCarriesWallpaper(Wallpapers.VIDEO_PREFIX + "a", listOf("a", "b"), true, "b"))
    }

    @Test fun sharingMustNotTurnASingleVideoSaverIntoARotatingPlaylist() {
        assertFalse(localSaverCarriesWallpaper(Wallpapers.VIDEOS, listOf("a", "b"), true, "a"))
        assertFalse(localSaverCarriesWallpaper(Wallpapers.VIDEO_PREFIX + "a", listOf("a", "b"), false, null))
        assertTrue(localSaverCarriesWallpaper(Wallpapers.VIDEOS, listOf("a", "b"), false, null))
    }

    @Test fun missingSelectionsCannotReuseAnUnrelatedWallpaper() {
        assertFalse(localSaverCarriesWallpaper(Wallpapers.VIDEO_PREFIX + "deleted", listOf("a"), true, "a"))
        assertFalse(localSaverCarriesWallpaper(Wallpapers.VIDEOS, emptyList(), true, null))
        assertFalse(localSaverCarriesWallpaper(Wallpapers.VIDEO_PREFIX + "a", listOf("a", "b"), true, "deleted"))
        assertTrue(localSaverCarriesWallpaper(Wallpapers.VIDEO_PREFIX + "a", listOf("a"), true, "deleted"))
    }

    @Test fun onlineDecoderHandoffGoesThroughTheInstalledCoverBeforeStopping() {
        val film = LiveFilm()
        film.frameReady = true
        var called = false
        film.handoff = { assertTrue(film.frameReady); called = true; film.frameReady = false }
        film.letDecoderGo()
        assertTrue(called)
        assertFalse(film.frameReady)
    }

    @Test fun localDecoderHandoffAlsoUsesItsNativeCoverPath() {
        val film = HomeVideo()
        film.frameReady = true
        var called = false
        film.handoff = { assertTrue(film.frameReady); called = true; film.frameReady = false }
        film.letDecoderGo()
        assertTrue(called)
        assertFalse(film.frameReady)
    }
}
