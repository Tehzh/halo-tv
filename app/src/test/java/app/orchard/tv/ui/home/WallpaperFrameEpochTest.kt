package app.orchard.tv.ui.home

import org.junit.Assert.*
import org.junit.Test

class WallpaperFrameEpochTest {
    @Test fun oldPosterCannotReplaceTheNextVideosFrame() {
        val frames = WallpaperFrameEpoch()
        val first = frames.begin("first")
        val second = frames.begin("second")
        assertFalse(frames.accepts(first))
        assertTrue(frames.accepts(second))
    }
    @Test fun restartingTheSameVideoInvalidatesItsPreviousCapture() {
        val frames = WallpaperFrameEpoch()
        val before = frames.begin("same")
        frames.invalidate()
        val after = frames.begin("same")
        assertFalse(frames.accepts(before))
        assertTrue(frames.accepts(after))
    }
    @Test fun detachedAndEmptySourcesRejectAllPendingWork() {
        val frames = WallpaperFrameEpoch()
        assertFalse(frames.accepts(frames.current()))
        val previous = frames.begin("clip")
        frames.invalidate()
        assertFalse(frames.accepts(previous))
        assertFalse(frames.accepts(frames.begin(null)))
    }
}
