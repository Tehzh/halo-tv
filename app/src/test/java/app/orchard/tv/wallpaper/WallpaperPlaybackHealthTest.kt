package app.orchard.tv.wallpaper

import org.junit.Assert.*
import org.junit.Test

class WallpaperPlaybackHealthTest {
    @Test fun sustainedUhdDropsCanRecoverWithoutOverridingAnExplicitQuality() {
        assertTrue(WallpaperPlaybackHealth.shouldLower(true, AerialQuality.Uhd, 7000, 50, 150))
        assertFalse(WallpaperPlaybackHealth.shouldLower(false, AerialQuality.Uhd, 7000, 50, 150))
        assertFalse(WallpaperPlaybackHealth.shouldLower(true, AerialQuality.Hd, 7000, 50, 150))
    }
    @Test fun transitionOrOccasionalDropsDoNotTriggerAPlayerRestart() {
        assertFalse(WallpaperPlaybackHealth.shouldLower(true, AerialQuality.Uhd, 1000, 50, 150))
        assertFalse(WallpaperPlaybackHealth.shouldLower(true, AerialQuality.Uhd, 7000, 3, 150))
        assertFalse(WallpaperPlaybackHealth.shouldLower(true, AerialQuality.Uhd, 7000, 50, 1000))
        assertFalse(WallpaperPlaybackHealth.shouldLower(true, AerialQuality.Uhd, 7000, 40, 1))
    }
}
