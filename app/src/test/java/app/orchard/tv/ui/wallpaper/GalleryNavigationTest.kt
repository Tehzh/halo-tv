package app.orchard.tv.ui.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryNavigationTest {
    @Test fun exitsOnlyFromTheRightmostPopulatedCell() {
        assertFalse(galleryRowEndsAt(0, 7, 3))
        assertFalse(galleryRowEndsAt(1, 7, 3))
        assertTrue(galleryRowEndsAt(2, 7, 3))
        assertTrue(galleryRowEndsAt(5, 7, 3))
        assertTrue(galleryRowEndsAt(6, 7, 3))
        assertFalse(galleryRowEndsAt(7, 7, 3))
        assertFalse(galleryRowEndsAt(0, 7, 0))
    }
}
