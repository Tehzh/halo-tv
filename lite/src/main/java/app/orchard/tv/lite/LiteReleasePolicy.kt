package app.orchard.tv.lite

import java.net.URI
import java.util.Locale

/** Pure policy shared by metadata parsing and download redirects; no Android dependency. */
internal object LiteReleasePolicy {
    const val WEBSITE = "https://halo-tv.pages.dev"
    const val RELEASES = "https://github.com/Null9797/halo-tv/releases"
    fun compare(a: String, b: String): Int {
        val aa = a.split('.').map { it.toLongOrNull() ?: 0L }
        val bb = b.split('.').map { it.toLongOrNull() ?: 0L }
        for (i in 0 until maxOf(aa.size, bb.size)) {
            val c = aa.getOrElse(i) { 0L }.compareTo(bb.getOrElse(i) { 0L })
            if (c != 0) return c
        }
        return 0
    }
    fun newer(version: String, code: Long?, current: String, currentCode: Long): Boolean =
        compare(version, current) >= 0 && (if (code != null) code > currentCode else compare(version, current) > 0)

    fun liteName(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return (n.startsWith("halotv-") || n.startsWith("aerial-universal-")) && n.endsWith("-lite.apk")
    }

    fun trustedPackage(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && u.port in listOf(-1, 443) && u.fragment == null &&
            liteName(u.path.substringAfterLast('/')) &&
            ((u.host == "halo-tv.pages.dev" && u.path.startsWith("/updates/")) ||
                (u.host == "github.com" && u.path.startsWith("/Null9797/halo-tv/releases/download/")))
    }.getOrDefault(false)

    fun trustedRedirect(url: String): Boolean = trustedPackage(url) || runCatching {
        val u = URI(url)
        u.scheme == "https" && u.userInfo == null && u.port in listOf(-1, 443) && u.fragment == null &&
            u.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)
}
