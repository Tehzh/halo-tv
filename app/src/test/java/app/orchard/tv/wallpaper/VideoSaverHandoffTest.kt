package app.orchard.tv.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoSaverHandoffTest {
    @Test fun holdsOnlyUploadedVideosBehindFlipClock() {
        assertTrue(Wallpapers.holdVideoForSaver("video:sample", ScreensaverMode.FlipClock))
        assertTrue(Wallpapers.holdVideoForSaver(Wallpapers.VIDEOS, ScreensaverMode.FlipClock))
        assertFalse(Wallpapers.holdVideoForSaver("video:sample", ScreensaverMode.Videos))
        assertFalse(Wallpapers.holdVideoForSaver("video:sample", ScreensaverMode.Aerial))
        assertFalse(Wallpapers.holdVideoForSaver(Wallpapers.LIVE, ScreensaverMode.FlipClock))
    }
}
