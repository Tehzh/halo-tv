package app.orchard.tv.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class OnlineRotationTest {
    @Test fun avoidsImmediateRepeats() {
        val random = Random(17)
        repeat(500) { assertNotEquals(2, randomOnlineIndex(2, 5, random)) }
        assertEquals(0, randomOnlineIndex(0, 1, random))
    }
    @Test fun allowsFirstSelectionAcrossEntireList() {
        val random = Random(30)
        val choices = (0 until 500).map { randomOnlineIndex(-1, 3, random) }.toSet()
        assertEquals(setOf(0, 1, 2), choices)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyList() { randomOnlineIndex(-1, 0) }
}
