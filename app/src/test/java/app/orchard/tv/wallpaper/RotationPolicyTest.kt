package app.orchard.tv.wallpaper

import org.junit.Assert.*
import org.junit.Test

class RotationPolicyTest {
    @Test fun disabledNeverRotates() {
        assertFalse(rotationDue(0, 1, 100, returnedHome = true, applyNow = true))
    }
    @Test fun returnModeDoesNotRunFromTimer() {
        assertFalse(rotationDue(-1, 1, Long.MAX_VALUE))
        assertTrue(rotationDue(-1, 0, 100, returnedHome = true))
        assertTrue(rotationDue(-1, 0, 100, applyNow = true))
    }
    @Test fun dailyAndCustomIgnoreReturnUntilDue() {
        for (minutes in listOf(30, 60, 1440, 17)) {
            assertFalse(rotationDue(minutes, 101, 100, returnedHome = true))
            assertTrue(rotationDue(minutes, 100, 100))
            assertFalse(rotationDue(minutes, 0, 100))
        }
    }
}
