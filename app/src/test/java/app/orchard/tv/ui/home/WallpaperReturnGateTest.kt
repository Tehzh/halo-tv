package app.orchard.tv.ui.home

import app.orchard.tv.ui.components.WallpaperReturnGate
import org.junit.Assert.*
import org.junit.Test

class WallpaperReturnGateTest {
    @Test fun returningWindowWaitsForItsOwnSubmittedVideoFrame() {
        val gate = WallpaperReturnGate()
        val beforeStop = gate.token()
        val returning = gate.cover()
        assertFalse(gate.reveal(beforeStop))
        assertTrue(gate.covered)
        gate.rendered()
        assertTrue(gate.reveal(returning))
        assertFalse(gate.covered)
    }
    @Test fun secondStopInvalidatesAnAlreadyScheduledReveal() {
        val gate = WallpaperReturnGate()
        gate.cover()
        val firstReturn = gate.rendered()
        gate.cover()
        assertFalse(gate.reveal(firstReturn))
        assertTrue(gate.covered)
    }
    @Test fun aCoverCannotBeRemovedBeforeAnyVideoFrame() {
        val gate = WallpaperReturnGate()
        assertFalse(gate.reveal(gate.token()))
        assertTrue(gate.covered)
    }
    @Test fun readinessCanBeCommittedAfterAnEarlyFrameCallback() {
        val gate = WallpaperReturnGate()
        val token = gate.rendered()
        assertTrue(gate.frameReady)
        assertTrue(gate.reveal(token))
        gate.cover()
        assertFalse(gate.frameReady)
        assertFalse(gate.reveal(token))
    }
}
