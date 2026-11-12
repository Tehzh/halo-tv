package app.orchard.tv.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSaverSelectionTest {
    @Test fun rotateAllKeepsEveryItemAndOrder() {
        assertEquals(listOf("a", "b", "c"), LocalSaverSelection.pick(listOf("a", "b", "c"), false, "b") { it })
    }
    @Test fun singleKeepsOnlyTheChosenId() {
        assertEquals(listOf("b"), LocalSaverSelection.pick(listOf("a", "b", "c"), true, "b") { it })
    }
    @Test fun deletedOrUnchosenItemUsesOneRemainingItem() {
        assertEquals(listOf("a"), LocalSaverSelection.pick(listOf("a", "c"), true, "b") { it })
        assertEquals(listOf("a"), LocalSaverSelection.pick(listOf("a", "c"), true, null) { it })
    }
    @Test fun emptyLibraryDoesNotCrashOrInventAFile() {
        assertEquals(emptyList<String>(), LocalSaverSelection.pick(emptyList<String>(), true, "deleted") { it })
    }
}
