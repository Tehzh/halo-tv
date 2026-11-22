package app.orchard.tv.ui.home

import org.junit.Assert.*
import org.junit.Test

class SurfaceCopyHealthTest {
    @Test fun oneDarkFrameDoesNotDisableCapturing() {
        val health = SurfaceCopyHealth()
        health.result(false, 100)
        assertTrue(health.canTry(101)); assertNull(health.readable)
        health.result(true, 102)
        assertEquals(true, health.readable)
    }
    @Test fun threeFailuresAfterAnEarlierSuccessStillBackOff() {
        val health = SurfaceCopyHealth()
        health.result(true, 100)
        repeat(3) { health.result(false, 200L + it) }
        assertFalse(health.canTry(203)); assertEquals(false, health.readable)
        assertTrue(health.canTry(30_202))
        health.result(true, 30_203)
        assertTrue(health.canTry(30_204)); assertEquals(true, health.readable)
    }
    @Test fun successfulFramesResetConsecutiveFailures() {
        val health = SurfaceCopyHealth()
        repeat(20) { i -> health.result(false, i * 3L); health.result(true, i * 3L + 1) }
        assertTrue(health.canTry(100)); assertEquals(true, health.readable)
    }
}
