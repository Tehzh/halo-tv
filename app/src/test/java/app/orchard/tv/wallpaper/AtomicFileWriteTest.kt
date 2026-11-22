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
    @Test fun unsuccessfulStreamKeepsThePreviousFile() {
        val target = folder.newFile("icon.png")
        target.writeText("old image")
        assertFalse(target.replaceWithOutput { it.write("partial".toByteArray()); false })
        assertEquals("old image", target.readText())
        assertEquals(listOf("icon.png"), folder.root.listFiles()!!.map { it.name })
    }

    @Test fun throwingStreamKeepsThePreviousFile() {
        val target = folder.newFile("icon.png")
        target.writeText("old image")
        assertTrue(runCatching { target.replaceWithOutput { it.write(1); throw java.io.IOException("interrupted") } }.isFailure)
        assertEquals("old image", target.readText())
        assertEquals(listOf("icon.png"), folder.root.listFiles()!!.map { it.name })
    }

    @Test fun successfulStreamReplacesThePreviousFile() {
        val target = folder.newFile("icon.png")
        target.writeText("old image")
        assertTrue(target.replaceWithOutput { it.write("new image".toByteArray()); true })
        assertEquals("new image", target.readText())
        assertEquals(listOf("icon.png"), folder.root.listFiles()!!.map { it.name })
    }
}
