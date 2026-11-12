package app.orchard.tv.lite

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/** Only same-package, same-signer, higher-version Lite APKs may reach the installer. */
internal object LiteUpdate {
    private const val MAX = 64L * 1024 * 1024
    private var receiving = false
    private fun directory(context: Context) = File(context.cacheDir, "halo-update").apply { mkdirs() }
    fun file(context: Context) = File(directory(context), "pending.apk")

    @Suppress("DEPRECATION")
    private fun valid(context: Context, apk: File): String {
        val pm = context.packageManager
        val next = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES) ?: return "wrong_app"
        val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        if (next.packageName != context.packageName) return "wrong_variant"
        fun fingerprints(info: android.content.pm.PackageInfo) = info.signatures?.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }?.toSet().orEmpty()
        val incoming = fingerprints(next)
        val installed = fingerprints(current)
        if (incoming.isEmpty() || installed.isEmpty()) return "signature_unavailable"
        if (incoming != installed) return "installed_signature_mismatch"
        val newCode = if (Build.VERSION.SDK_INT >= 28) next.longVersionCode else next.versionCode.toLong()
        val currentCode = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
        if (newCode <= currentCode) return "not_newer"
        if (Build.VERSION.SDK_INT >= 24 && (next.applicationInfo?.minSdkVersion ?: 0) > Build.VERSION.SDK_INT) return "unsupported"
        return "ready"
    }

    fun ready(context: Context): Boolean = file(context).let { it.isFile && runCatching { valid(context, it) == "ready" }.getOrDefault(false) }

    /** An already staged newer package must not suppress downloading a later release. */
    fun readyFor(context: Context, version: String, code: Long?): Boolean = runCatching {
        val staged = file(context)
        if (!ready(context)) return@runCatching false
        val info = context.packageManager.getPackageArchiveInfo(staged.path, 0) ?: return@runCatching false
        val stagedCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        LiteReleasePolicy.compare(info.versionName.orEmpty().substringBefore('-'), version) >= 0 && (code == null || stagedCode >= code)
    }.getOrDefault(false)

    fun receive(context: Context, input: InputStream, length: Long): String {
        synchronized(this) {
            if (receiving) return "busy"
            receiving = true
        }
        val tmp = File(directory(context), "receiving.part")
        try {
            if (length !in 1..MAX) return "invalid_size"
            val fs = StatFs(tmp.parent)
            @Suppress("DEPRECATION")
            val space = if (Build.VERSION.SDK_INT >= 18) fs.availableBytes else fs.availableBlocks.toLong() * fs.blockSize
            if (space < length * 2 + 8 * 1024 * 1024) return "no_space"
            FileOutputStream(tmp).use { out ->
                val buffer = ByteArray(16 * 1024)
                var left = length
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                    if (n < 0) return "transfer_failed"
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
            val reason = valid(context, tmp)
            if (reason != "ready") return reason
            if (!tmp.renameTo(file(context))) return "transfer_failed"
            return "ready"
        } catch (_: Exception) {
            return "transfer_failed"
        } finally {
            tmp.delete()
            synchronized(this) { receiving = false }
        }
    }

    /** Bounded HTTPS transfer into the same validated, atomic pending slot as phone uploads. */
    fun receiveOnline(context: Context, address: String, sha256: String?, expectedCode: Long?, progress: (Int) -> Unit): String {
        if (!LiteReleasePolicy.trustedPackage(address)) return "wrong_app"
        synchronized(this) {
            if (receiving) return "busy"
            receiving = true
        }
        val tmp = File(directory(context), "online.part")
        var connection: java.net.HttpURLConnection? = null
        try {
            var next = address
            for (hop in 0..5) {
                if (!LiteReleasePolicy.trustedRedirect(next)) return "download_failed"
                val c = Net.open(next)
                connection = c
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "HaloTV-Lite-Updater/3.0")
                if (c.responseCode in listOf(301, 302, 303, 307, 308)) {
                    val location = c.getHeaderField("Location") ?: return "download_failed"
                    next = URI(next).resolve(location).toString()
                    c.disconnect(); connection = null
                    if (hop == 5) return "download_failed"
                } else break
            }
            val c = connection ?: return "download_failed"
            if (c.responseCode != 200) return "download_failed"
            val length = c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            if (length > MAX || length == 0L) return "invalid_size"
            val fs = StatFs(tmp.parent)
            @Suppress("DEPRECATION")
            val free = if (Build.VERSION.SDK_INT >= 18) fs.availableBytes else fs.availableBlocks.toLong() * fs.blockSize
            if (free < maxOf(length, 0L) + 8 * 1024 * 1024L) return "no_space"
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            var lastPercent = -1
            c.inputStream.use { input -> FileOutputStream(tmp).use { out ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    count += n
                    if (count > MAX) return "invalid_size"
                    if (count + 8 * 1024 * 1024L > free) return "no_space"
                    digest.update(buffer, 0, n); out.write(buffer, 0, n)
                    val pct = if (length > 0) (count * 100L / length).coerceIn(0, 99).toInt() else 0
                    if (pct != lastPercent) { progress(pct); lastPercent = pct }
                }
            } }
            if (count <= 0L || (length > 0L && count != length)) return "transfer_failed"
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha256 != null && !sha256.equals(actualSha, ignoreCase = true)) return "checksum"
            val result = valid(context, tmp)
            if (result != "ready") return result
            if (expectedCode != null) {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageArchiveInfo(tmp.path, 0) ?: return "wrong_app"
                @Suppress("DEPRECATION")
                val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                if (code != expectedCode) return "wrong_app"
            }
            if (!tmp.renameTo(file(context))) return "transfer_failed"
            progress(100)
            return "ready"
        } catch (_: Exception) {
            return "download_failed"
        } finally {
            connection?.disconnect(); tmp.delete()
            synchronized(this) { receiving = false }
        }
    }

    fun install(context: Context): Boolean {
        if (!ready(context)) return false
        return runCatching {
            val uri = installerUri(context)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /** Old installers parse a real file path and cannot open our private pending slot. */
    internal fun installerUri(context: Context): Uri {
        if (Build.VERSION.SDK_INT >= 24) return Uri.parse("content://${context.packageName}.updates/pending.apk")
        val external = checkNotNull(context.externalCacheDir) { "External update cache unavailable" }
        val directory = File(external, "halo-update-install")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create installer cache" }
        val shared = File(directory, "HaloTV-Lite.apk")
        val part = File(directory, "HaloTV-Lite.part")
        try {
            file(context).inputStream().use { input -> FileOutputStream(part).use { output ->
                input.copyTo(output, 16 * 1024)
                output.fd.sync()
            } }
            check(part.setReadable(true, false) || part.canRead()) { "Cannot share installer file" }
            check(part.renameTo(shared)) { "Cannot prepare installer file" }
        } finally {
            part.delete()
        }
        return Uri.fromFile(shared)
    }
}

/** A single read-only file, with a temporary URI permission granted only to the system installer. */
class LiteUpdateProvider : ContentProvider() {
    override fun onCreate() = true
    private fun requireFile(uri: Uri): File {
        require(uri.path == "/pending.apk")
        return LiteUpdate.file(checkNotNull(context)).also { check(it.isFile) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        return ParcelFileDescriptor.open(requireFile(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val file = requireFile(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map<String, Any?> { when (it) { OpenableColumns.DISPLAY_NAME -> "HaloTV-Lite.apk"; OpenableColumns.SIZE -> file.length(); else -> null } }.toTypedArray())
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
