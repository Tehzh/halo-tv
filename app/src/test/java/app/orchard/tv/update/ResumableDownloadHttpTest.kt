package app.orchard.tv.update

import java.io.File
import java.net.ServerSocket
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableDownloadHttpTest {
    @Test fun actualHttpTruncationResumesWithTheEntityValidator() {
        val server = ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5_000
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val worker = Thread {
            try {
                repeat(2) { index ->
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val headers = StringBuilder()
                        while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; headers.appendLine(line) }
                        requests.add(headers.toString())
                        val reply = if (index == 0)
                            "HTTP/1.1 200 OK\r\nContent-Length: 6\r\nETag: \"v1\"\r\nConnection: close\r\n\r\nabc"
                        else "HTTP/1.1 206 Partial Content\r\nContent-Length: 3\r\nContent-Range: bytes 3-5/6\r\nETag: \"v1\"\r\nConnection: close\r\n\r\ndef"
                        socket.getOutputStream().write(reply.toByteArray(Charsets.US_ASCII))
                    }
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        val part = File.createTempFile("halo-http", ".part")
        try {
            runBlocking { ResumableDownload.fetch("http://127.0.0.1:${server.localPort}/apk", part, 1024, 30_000, 1, { 0L }) }
            worker.join(5_000)
            assertEquals("abcdef", part.readText())
            assertEquals(2, requests.size)
            assertTrue(requests[1].contains("Range: bytes=3-", ignoreCase = true))
            assertTrue(requests[1].contains("If-Range: \"v1\"", ignoreCase = true))
        } finally { server.close(); part.delete() }
    }
}
