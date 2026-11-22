package app.orchard.tv.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AerialRetryTest {
    @Test fun onlyThirdFailureOfCurrentItemSkipsAPlaylist() {
        assertFalse(shouldSkipAerialOnRetry(1, 3, "a", "a"))
        assertTrue(shouldSkipAerialOnRetry(3, 3, "a", "a"))
        assertFalse(shouldSkipAerialOnRetry(3, 1, "a", "a"))
        assertFalse(shouldSkipAerialOnRetry(3, 3, "a", "b"))
        assertFalse(shouldSkipAerialOnRetry(3, 3, null, null))
    }
}
