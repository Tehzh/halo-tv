package app.orchard.tv.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.orchard.tv.settings.Restricted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Receives an update through the phone settings page. Only the TV can confirm installation. */
internal object PhoneUpdate {
    private const val TRANSFER_LIMIT_MS = 5 * 60 * 1000L
    private const val CONFIRM_LIMIT_MS = 10 * 60 * 1000L
    private const val RESERVE_BYTES = 16L * 1024 * 1024
    private val handler = Handler(Looper.getMainLooper())
    private val receiving = AtomicBoolean()

    internal data class Ready(val apk: File, val version: String)

    var ready by mutableStateOf<Ready?>(null)
        private set

    fun discard(upload: Ready) {
        if (ready !== upload) return
        ready = null
        upload.apk.delete()
    }

    fun confirm(context: Context, upload: Ready) {
        if (ready !== upload || Restricted.active) return
        Updates.installFromPhone(context, upload.apk)
        // The installer takes ownership only after any permission prompt is resolved.
        if (!upload.apk.exists()) discard(upload)
    }

    fun receive(context: Context, length: Long, input: InputStream): String {
        if (length !in 1..Updates.MAX_APK_BYTES) return "invalid_size"
        if (!receiving.compareAndSet(false, true)) return "busy"
        var upload: File? = null
        var retained = false
        return try {
            val unavailable = runBlocking(Dispatchers.Main) {
                when {
                    Restricted.active -> "restricted"
                    ready != null -> "busy"
                    else -> null
                }
            }
            if (unavailable != null) return unavailable
            val directory = File(context.cacheDir, "phone-update")
            check(directory.isDirectory || directory.mkdirs())
            if (directory.usableSpace < length * 2 + RESERVE_BYTES) return "no_space"
            val file = File.createTempFile("update-", ".apk", directory)
            upload = file
            val deadline = SystemClock.elapsedRealtime() + TRANSFER_LIMIT_MS
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    if (SystemClock.elapsedRealtime() >= deadline) return "transfer_failed"
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count <= 0) return "transfer_failed"
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
            val result = Updates.validatePhoneApk(context, file)
            val version = result.version ?: return result.error ?: "wrong_app"
            retained = runBlocking(Dispatchers.Main) {
                if (Restricted.active) false else {
                    val pending = Ready(file, version)
                    ready = pending
                    handler.postDelayed({ discard(pending) }, CONFIRM_LIMIT_MS)
                    true
                }
            }
            if (retained) "ready" else "restricted"
        } catch (_: Exception) {
            "transfer_failed"
        } finally {
            if (!retained) upload?.delete()
            receiving.set(false)
        }
    }
}
