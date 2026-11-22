package app.orchard.tv.lite

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LiteNetworkBoundsTest {
    @Test fun boundedCopyAcceptsLimitAndRejectsOverLimit() {
        val out = ByteArrayOutputStream()
        assertEquals(3L, Net.copyBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), out, 3))
        try { Net.copyBounded(ByteArrayInputStream(ByteArray(4)), ByteArrayOutputStream(), 3); fail("oversized") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun thumbnailSampleStaysWithinLimits() {
        assertEquals(1, BingSheet.thumbnailSample(640, 360))
        assertEquals(8, BingSheet.thumbnailSample(3840, 2160))
        assertEquals(0, BingSheet.thumbnailSample(-1, 2160))
        assertEquals(0, BingSheet.thumbnailSample(0, 0))
    }

    @Test fun incompleteDownloadDoesNotReplaceExistingFileOrLeaveTemp() {
        val dir = createTempDir(prefix = "halo-net-")
        val target = File(dir, "tile.png").apply { writeText("old image") }
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5_000
        val worker = Thread {
            try {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 10\r\nConnection: close\r\n\r\nbad".toByteArray(Charsets.US_ASCII))
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        try {
            try { Net.download("http://127.0.0.1:${server.localPort}/tile", target); fail("incomplete") }
            catch (_: Exception) { }
            worker.join(5_000)
            assertEquals("old image", target.readText())
            assertEquals(listOf("tile.png"), dir.list()?.sorted())
        } finally { server.close(); dir.deleteRecursively() }
    }
}
