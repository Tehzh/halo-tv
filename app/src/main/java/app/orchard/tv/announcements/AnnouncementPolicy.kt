package app.orchard.tv.announcements

import java.net.URI

/** Plain-text release/service notices. No remote code, package installation or forced navigation. */
data class RemoteAnnouncement(
    val id: String,
    val revision: Int,
    val title: String,
    val body: String,
    val important: Boolean = false,
    val variants: Set<String> = emptySet(),
    val minVersion: String? = null,
    val maxVersion: String? = null,
    val minCode: Long? = null,
    val maxCode: Long? = null,
    val startsAt: Long = 0L,
    val expiresAt: Long = Long.MAX_VALUE,
    val detailsUrl: String? = null,
) {
    val key: String get() = "$id:$revision"
}

internal object AnnouncementPolicy {
    const val MAX_BYTES = 64 * 1024
    const val CHECK_MS = 3 * 60 * 60_000L
    const val RETRY_MS = 10 * 60_000L
    const val SNOOZE_MS = 24 * 60 * 60_000L

    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toLongOrNull() ?: 0L }
        val y = b.split('.').map { it.toLongOrNull() ?: 0L }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0L }.compareTo(y.getOrElse(i) { 0L })
            if (d != 0) return d
        }
        return 0
    }

    fun applies(n: RemoteAnnouncement, variant: String, version: String, code: Long, now: Long): Boolean =
        now >= n.startsAt && now < n.expiresAt &&
            (n.variants.isEmpty() || variant in n.variants) &&
            (n.minVersion == null || compare(version, n.minVersion) >= 0) &&
            (n.maxVersion == null || compare(version, n.maxVersion) <= 0) &&
            (n.minCode == null || code >= n.minCode) && (n.maxCode == null || code <= n.maxCode)

    /** Details are opened only after a click and only on Halo's own site/repository. */
    fun trustedDetails(url: String): Boolean = runCatching {
        val u = URI(url)
        if (u.scheme != "https" || u.rawUserInfo != null || u.port !in listOf(-1, 443)) return false
        // Browsers resolve dot segments before navigation, including percent-encoded ones.
        val path = u.path.orEmpty()
        if ('\\' in path || path.split('/').any { it == "." || it == ".." }) return false
        (u.host == "halo-tv.pages.dev") ||
            (u.host == "github.com" && (path == "/Null9797/halo-tv" || path.startsWith("/Null9797/halo-tv/")))
    }.getOrDefault(false)

    /** True only for the static feed, including redirects. */
    fun trustedFeed(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.rawUserInfo == null && u.port in listOf(-1, 443) && u.rawQuery == null && u.rawFragment == null &&
            ((u.host == "halo-tv.pages.dev" && u.path == "/announcements.json") ||
             (u.host == "raw.githubusercontent.com" && u.path == "/Null9797/halo-tv/main/announcements.json"))
    }.getOrDefault(false)
}
