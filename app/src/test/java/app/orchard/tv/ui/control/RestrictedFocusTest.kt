package app.orchard.tv.ui.control

import org.junit.Assert.assertEquals
import org.junit.Test

class RestrictedFocusTest {
    @Test fun choosesFirstAvailableRestrictedControl() {
        assertEquals(RestrictedFocusTarget.Volume, restrictedFocusTarget(true, true))
        assertEquals(RestrictedFocusTarget.Volume, restrictedFocusTarget(true, false))
        assertEquals(RestrictedFocusTarget.Brightness, restrictedFocusTarget(false, true))
        assertEquals(RestrictedFocusTarget.Leave, restrictedFocusTarget(false, false))
    }
}
