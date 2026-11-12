package app.orchard.tv.wallpaper

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFileWriteTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun replacingAnExistingCachePublishesTheCompleteNewValue() {
        val target = folder.newFile("index.json")
        target.writeText("old")
        val value = "new-value".repeat(10000).toByteArray()
        target.replaceContents(value)
        assertArrayEquals(value, target.readBytes())
        assertEquals(listOf("index.json"), folder.root.listFiles()!!.map { it.name })
    }

    @Test fun failedPublicationRemovesItsTemporaryFile() {
        val target = folder.newFolder("index.json")
        assertTrue(runCatching { target.replaceContents(byteArrayOf(1, 2, 3)) }.isFailure)
        assertTrue(target.isDirectory)
        assertEquals(listOf("index.json"), folder.root.listFiles()!!.map { it.name })
    }
}
