package app.orchard.tv.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResumableDownloadTest {
    private class Response(
        private val status: Int, private val body: ByteArray, private val declared: Int,
        private val tag: String? = "\"v1\"", private val range: String? = null, private val failInput: Boolean = false,
        finalUrl: String = "http://example.test/file",
    ) : HttpURLConnection(URL(finalUrl)) {
        val sent = mutableMapOf<String, String>()
        override fun setRequestProperty(key: String, value: String) { sent[key] = value }
        override fun getResponseCode() = status
        override fun getContentLength() = declared
        override fun getHeaderField(name: String): String? = when (name) { "ETag" -> tag; "Content-Range" -> range; else -> null }
        override fun getInputStream(): java.io.InputStream {
            if (failInput) throw IOException("body unavailable")
            return ByteArrayInputStream(body)
        }
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
    }

    private fun run(part: File, responses: List<Response>) = runBlocking {
        val queue = ArrayDeque(responses)
        ResumableDownload.fetch("http://example.test/file", part, 1024, 30_000, 1, { 0L }, connect = { queue.removeFirstOrNull() ?: throw IOException("test responses exhausted") })
    }

    @Test fun resumesOnlyMatchingEntityAndRange() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            val first = Response(200, "abc".toByteArray(), 6)
            val second = Response(206, "def".toByteArray(), 3, range = "bytes 3-5/6")
            run(part, listOf(first, second))
            assertEquals("abcdef", part.readText())
            assertEquals("bytes=3-", second.sent["Range"])
            assertEquals("\"v1\"", second.sent["If-Range"])
        } finally { part.delete() }
    }

    @Test fun changedTagOrWrongRangeIsRejectedAndPartialRemoved() {
        for (second in listOf(Response(206, "def".toByteArray(), 3, "\"v2\"", "bytes 3-5/6"), Response(206, "def".toByteArray(), 3, range = "bytes 2-4/6"))) {
            val part = File.createTempFile("halo-resume", ".part")
            try {
                try { run(part, listOf(Response(200, "abc".toByteArray(), 6), second)); fail("must reject") }
                catch (_: IOException) { assertFalse(part.exists()) }
            } finally { part.delete() }
        }
    }

    @Test fun chunkedRangeMustMatchItsDeclaredEnd() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            try {
                run(part, listOf(Response(200, "abc".toByteArray(), 6), Response(206, "def".toByteArray(), -1, range = "bytes 3-4/6")))
                fail("must reject mismatched range body")
            } catch (_: IOException) { assertFalse(part.exists()) }
        } finally { part.delete() }
    }

    @Test fun sameTagWithChangedEntityLengthIsRejected() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            try {
                run(part, listOf(Response(200, "abc".toByteArray(), 6), Response(206, "defg".toByteArray(), 4, range = "bytes 3-6/7")))
                fail("must reject a changed entity length")
            } catch (_: IOException) { assertFalse(part.exists()) }
        } finally { part.delete() }
    }

    @Test fun ignoredRangeReplacesInsteadOfAppending() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            run(part, listOf(Response(200, "abc".toByteArray(), 6), Response(200, "ABCDEF".toByteArray(), 6, "\"v2\"")))
            assertEquals("ABCDEF", part.readText())
        } finally { part.delete() }
    }

    @Test fun cancellationDeletesPartialAndDoesNotRetry() {
        val part = File.createTempFile("halo-resume", ".part")
        var connections = 0
        try {
            try {
                runBlocking {
                    ResumableDownload.fetch("http://example.test/file", part, 1024, 30_000, 1, { 0L },
                        connect = { connections++; Response(200, "abc".toByteArray(), 6) },
                        progress = { throw kotlinx.coroutines.CancellationException("cancel") })
                }
                fail("must cancel")
            } catch (_: kotlinx.coroutines.CancellationException) {
                assertFalse(part.exists())
                assertEquals(1, connections)
            }
        } finally { part.delete() }
    }

    @Test fun declaredOrStreamedSizeOverLimitIsRejected() {
        for (response in listOf(Response(200, "abcdef".toByteArray(), 6), Response(200, "abcdef".toByteArray(), -1))) {
            val part = File.createTempFile("halo-resume", ".part")
            try {
                try {
                    runBlocking { ResumableDownload.fetch("http://example.test/file", part, 4, 30_000, 1, { 0L }, connect = { response }) }
                    fail("must reject oversized content")
                } catch (_: IOException) { assertFalse(part.exists()) }
            } finally { part.delete() }
        }
    }

    @Test fun interruptedFullReplacementCanResumeFromItsOwnNewOffset() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            val replacement = Response(200, "AB".toByteArray(), 6, "\"v2\"")
            val tail = Response(206, "CDEF".toByteArray(), 4, "\"v2\"", "bytes 2-5/6")
            run(part, listOf(Response(200, "abc".toByteArray(), 6), replacement, tail))
            assertEquals("ABCDEF", part.readText())
            assertEquals("bytes=2-", tail.sent["Range"])
            assertEquals("\"v2\"", tail.sent["If-Range"])
        } finally { part.delete() }
    }

    @Test fun replacementFailureCannotReusePreviousEntityPrefix() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            try {
                run(part, listOf(Response(200, "abc".toByteArray(), 6), Response(200, byteArrayOf(), 6, "\"v2\"", failInput = true)))
                fail("must stop when replacement body cannot be opened")
            } catch (_: IOException) { assertFalse(part.exists()) }
        } finally { part.delete() }
    }

    @Test fun changedFinalUrlDoesNotReusePartial() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            try {
                run(part, listOf(Response(200, "abc".toByteArray(), 6), Response(206, "def".toByteArray(), 3, range = "bytes 3-5/6", finalUrl = "http://other.test/file")))
                fail("must reject changed final URL")
            } catch (_: IOException) { assertFalse(part.exists()) }
        } finally { part.delete() }
    }

    @Test fun aShortValidRangeMayResumeAtTheActualNextByte() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            val last = Response(206, "ef".toByteArray(), 2, range = "bytes 4-5/6")
            run(part, listOf(Response(200, "abc".toByteArray(), 6), Response(206, "d".toByteArray(), 3, range = "bytes 3-5/6"), last))
            assertEquals("abcdef", part.readText())
            assertEquals("bytes=4-", last.sent["Range"])
        } finally { part.delete() }
    }

    @Test fun noStrongValidatorDoesNotResume() {
        val part = File.createTempFile("halo-resume", ".part")
        try {
            val complete = Response(200, "ABCDEF".toByteArray(), 6)
            run(part, listOf(Response(200, "abc".toByteArray(), 6, "W/\"v1\""), complete))
            assertEquals("ABCDEF", part.readText())
            assertNull(complete.sent["Range"])
        } finally { part.delete() }
    }
    @Test fun transientErrorBeforeFirstByteRetriesWithoutRange() {
        val part = File.createTempFile("halo-retry", ".part")
        try {
            val complete = Response(200, "complete".toByteArray(), 8)
            run(part, listOf(Response(503, byteArrayOf(), 0), complete))
            assertEquals("complete", part.readText())
            assertNull(complete.sent["Range"])
        } finally { part.delete() }
    }

    @Test fun retriesAreBoundedAndDoNotKeepBrokenFiles() {
        val part = File.createTempFile("halo-retry", ".part")
        var connections = 0
        try {
            runBlocking {
                ResumableDownload.fetch("http://example.test/file", part, 1024, 60_000, 1, { 0L },
                    connect = { connections++; Response(503, byteArrayOf(), 0) })
            }
            fail("must fail")
        } catch (_: IOException) {
            assertEquals(3, connections)
            assertFalse(part.exists())
        } finally { part.delete() }
    }

    @Test fun aSlowButAdvancingDownloadIsNotDiscarded() {
        val part = File.createTempFile("halo-slow", ".part")
        var clock = 0L
        try {
            runBlocking {
                ResumableDownload.fetch("http://example.test/file", part, 1024, 60_000, 1,
                    { clock.also { clock += 60_001 } }, connect = { Response(200, "a".toByteArray(), 1) })
            }
            assertEquals("a", part.readText())
        } finally { part.delete() }
    }

}
