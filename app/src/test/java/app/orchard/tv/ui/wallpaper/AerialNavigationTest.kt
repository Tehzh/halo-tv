package app.orchard.tv.ui.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AerialNavigationTest {
    @Test fun rightExitFollowsBothColumnsAndHandlesAnIncompleteRow() {
        assertFalse(aerialRowEndsAt(0, 5))
        assertTrue(aerialRowEndsAt(1, 5))
        assertFalse(aerialRowEndsAt(2, 5))
        assertTrue(aerialRowEndsAt(3, 5))
        assertTrue(aerialRowEndsAt(4, 5))
        assertFalse(aerialRowEndsAt(5, 5))
        assertFalse(aerialRowEndsAt(-1, 5))
    }
}
