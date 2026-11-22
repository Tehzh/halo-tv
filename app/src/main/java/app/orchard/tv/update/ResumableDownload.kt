package app.orchard.tv.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A partial is reused only within one URL and only with a strong entity tag. */
internal object ResumableDownload {
    private class InvalidResponse(message: String) : IOException(message)
    private const val ATTEMPTS = 3
    private val contentRange = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    suspend fun fetch(
        url: String,
        part: File,
        maxBytes: Long,
        stallMs: Long,
        stallBytes: Long,
        now: () -> Long,
        connect: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
        configure: (HttpURLConnection) -> Unit = {},
        progress: suspend (Int) -> Unit = {},
    ) {
        part.delete()
        var validator: String? = null
        var expectedTotal: Long? = null
        var entityUrl: String? = null
        var lastError: IOException? = null
        try {
            repeat(ATTEMPTS) {
                currentCoroutineContext().ensureActive()
                val from = if (validator != null && part.isFile) part.length() else 0L
                val c = connect(url)
                var attemptStart = from
                try {
                    configure(c)
                    c.connectTimeout = 10_000
                    c.readTimeout = 60_000
                    c.setRequestProperty("Accept-Encoding", "identity")
                    if (from > 0) {
                        c.setRequestProperty("Range", "bytes=$from-")
                        c.setRequestProperty("If-Range", validator)
                    }
                    val code = c.responseCode
                    val encoding = c.getHeaderField("Content-Encoding")
                    if (!encoding.isNullOrBlank() && !encoding.equals("identity", true)) throw InvalidResponse("encoded response")
                    val responseTag = c.getHeaderField("ETag")?.trim()?.takeIf { it.startsWith('"') && it.endsWith('"') && !it.startsWith("W/") }
                    val append = from > 0 && code == HttpURLConnection.HTTP_PARTIAL
                    val length = c.contentLength.toLong()
                    val total: Long
                    var rangeEnd: Long? = null
                    if (append) {
                        if (entityUrl != c.url.toExternalForm()) throw InvalidResponse("changed final URL")
                        if (responseTag == null || responseTag != validator) throw InvalidResponse("changed entity tag")
                        val match = contentRange.matchEntire(c.getHeaderField("Content-Range").orEmpty()) ?: throw InvalidResponse("invalid content range")
                        val start = match.groupValues[1].toLongOrNull() ?: throw InvalidResponse("invalid range start")
                        val end = match.groupValues[2].toLongOrNull() ?: throw InvalidResponse("invalid range end")
                        total = match.groupValues[3].toLongOrNull() ?: throw InvalidResponse("invalid range total")
                        if (expectedTotal != null && total != expectedTotal) throw InvalidResponse("changed entity length")
                        expectedTotal = total
                        if (start != from || end < start || end >= total || (length >= 0 && length != end - start + 1)) throw InvalidResponse("inconsistent content range")
                        rangeEnd = end
                    } else {
                        if (code != HttpURLConnection.HTTP_OK) {
                            if (code in 400..499 && code != 408 && code != 429) throw InvalidResponse("HTTP $code")
                            throw IOException("HTTP $code")
                        }
                        // Discard the old entity before adopting a new validator or opening the body.
                        if (part.exists() && !part.delete()) throw InvalidResponse("cannot discard old partial")
                        validator = responseTag
                        entityUrl = c.url.toExternalForm()
                        attemptStart = 0L
                        total = length
                        expectedTotal = length.takeIf { it >= 0 }
                    }
                    if (total > maxBytes || total == 0L) throw InvalidResponse("Invalid APK size")
                    var done = if (append) from else 0L
                    var shown = -1
                    var windowAt = now()
                    var windowFrom = done
                    c.inputStream.use { input ->
                        FileOutputStream(part, append).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                done += n
                                if (done > maxBytes || (total >= 0 && done > total)) throw InvalidResponse("APK too large or response exceeds declared size")
                                out.write(buffer, 0, n)
                                val t = now()
                                if (t - windowAt >= stallMs) {
                                    if (done - windowFrom < stallBytes) throw IOException("stalled download")
                                    windowAt = t
                                    windowFrom = done
                                }
                                val percent = if (total > 0) (done * 100 / total).toInt() else 0
                                if (percent != shown) { shown = percent; progress(percent) }
                            }
                        }
                    }
                    if (rangeEnd != null && done < rangeEnd + 1) throw IOException("incomplete range body")
                    if (rangeEnd != null && done > rangeEnd + 1) throw InvalidResponse("range body length mismatch")
                    if (done == 0L || (total >= 0 && done != total)) throw IOException("incomplete response")
                    return
                } catch (e: IOException) {
                    lastError = e
                    // Transient failures before the first byte and responses without ETags also get
                    // bounded retries. Only a strong validator permits appending a partial.
                    if (e is InvalidResponse) throw e
                    if (validator == null || !part.isFile || part.length() <= attemptStart) {
                        part.delete()
                        validator = null
                        expectedTotal = null
                        entityUrl = null
                    }
                } finally {
                    c.disconnect()
                }
            }
            throw lastError ?: IOException("download failed")
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }
}
