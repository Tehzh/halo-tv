package app.orchard.tv.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackRecoveryTest {
    @Test fun skipsOnlyAfterThreeConsecutiveFailuresAndResetsForNextItem() {
        val recovery = VideoPlaybackRecovery()
        assertTrue(recovery.retryCurrent())
        assertTrue(recovery.retryCurrent())
        assertFalse(recovery.retryCurrent())
        assertTrue(recovery.retryCurrent())
    }

    @Test fun aRenderedFrameClearsTheFailureHistory() {
        val recovery = VideoPlaybackRecovery()
        assertTrue(recovery.retryCurrent())
        assertTrue(recovery.retryCurrent())
        recovery.frameRendered()
        assertTrue(recovery.retryCurrent())
        assertTrue(recovery.retryCurrent())
        assertFalse(recovery.retryCurrent())
    }
}
