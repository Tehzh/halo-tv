package app.orchard.tv.announcements

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.concurrent.CopyOnWriteArrayList

/** Shares the update-check entry points, without a service, timer, analytics or device identifier. */
object Announcements {
    const val WEBSITE_FEED = "https://halo-tv.pages.dev/announcements.json"
    const val GITHUB_FEED = "https://raw.githubusercontent.com/Null9797/halo-tv/main/announcements.json"
    private var prefs: SharedPreferences? = null
    private var variant = ""
    private var version = ""
    private var code = 0L
    private var notices: List<RemoteAnnouncement> = emptyList()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val presented = HashSet<String>()
    private var checking = false
    private var lastAttempt = Long.MIN_VALUE / 2
    private val main = Handler(Looper.getMainLooper())

    fun init(context: Context, variant: String, version: String, code: Long) {
        if (prefs != null) return
        this.variant = variant; this.version = version; this.code = code
        val p = context.applicationContext.getSharedPreferences("halo_announcements", Context.MODE_PRIVATE)
        prefs = p
        notices = runCatching { AnnouncementFeed.parse(p.getString("feed", "").orEmpty()) }.getOrDefault(emptyList())
    }

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.forEach { it() } }
    private fun eligible(): Sequence<RemoteAnnouncement> {
        val now = System.currentTimeMillis()
        return notices.asSequence().filter { AnnouncementPolicy.applies(it, variant, version, code, now) }
    }
    fun contains(n: RemoteAnnouncement): Boolean = eligible().any { it.key == n.key }
    fun latest(): RemoteAnnouncement? = eligible().firstOrNull()
    fun unread(): RemoteAnnouncement? = eligible().firstOrNull { prefs?.contains("read:${it.key}") != true }
    fun popup(): RemoteAnnouncement? = eligible().firstOrNull {
        it.important && prefs?.contains("read:${it.key}") != true && it.key !in presented &&
            System.currentTimeMillis() >= (prefs?.getLong("later:${it.key}", 0L) ?: 0L)
    }
    fun presented(n: RemoteAnnouncement) { presented.add(n.key) }
    fun acknowledge(n: RemoteAnnouncement) {
        prefs?.edit()?.putLong("read:${n.key}", System.currentTimeMillis())?.remove("later:${n.key}")?.apply()
        presented.add(n.key); trimHistory(); changed()
    }
    fun later(n: RemoteAnnouncement) {
        prefs?.edit()?.putLong("later:${n.key}", System.currentTimeMillis() + AnnouncementPolicy.SNOOZE_MS)?.apply()
        // The saved deadline controls snoozing even when a launcher lives for several days.
        presented.remove(n.key); trimHistory(); changed()
    }
    private fun trimHistory() {
        val p = prefs ?: return
        val history = p.all.filterKeys { it.startsWith("read:") || it.startsWith("later:") }
        if (history.size <= 100) return
        val remove = history.entries.sortedByDescending { (it.value as? Long) ?: 0L }.drop(100)
        val e = p.edit(); remove.forEach { e.remove(it.key) }; e.apply()
    }

    /** At most one bounded request, triggered by Home/network/Control Center's existing checks. */
    fun check(force: Boolean = false, maxAgeMs: Long = AnnouncementPolicy.CHECK_MS, networkCameUp: Boolean = false) {
        val p = prefs ?: return
        if (checking) return
        val wall = System.currentTimeMillis()
        val age = wall - p.getLong("checked_at", 0L)
        if (!force && age in 0 until maxAgeMs) return
        if (!force && !networkCameUp && SystemClock.elapsedRealtime() - lastAttempt < AnnouncementPolicy.RETRY_MS) return
        lastAttempt = SystemClock.elapsedRealtime(); checking = true
        Thread({
            val result = runCatching { read(WEBSITE_FEED) }.recoverCatching { read(GITHUB_FEED) }
            main.post {
                checking = false
                result.onSuccess { (text, fresh) ->
                    acceptFeed(text, fresh)
                }
                // Offline, absent or malformed feeds are silent; the last valid cache remains.
            }
        }, "Halo-Announcement").start()
    }

    /** Commit a parsed response on the main thread; unchanged empty feeds need no UI refresh. */
    internal fun acceptFeed(text: String, fresh: List<RemoteAnnouncement>) {
        val differs = fresh != notices
        notices = fresh
        presented.retainAll(fresh.map { it.key }.toSet())
        prefs?.edit()?.putString("feed", text)?.putLong("checked_at", System.currentTimeMillis())?.apply()
        if (differs) changed()
    }

    private fun read(initial: String): Pair<String, List<RemoteAnnouncement>> {
        var url = initial
        repeat(3) {
            require(AnnouncementPolicy.trustedFeed(url))
            val c = app.orchard.tv.system.CompatibleHttp.open(url)
            c.instanceFollowRedirects = false
            c.connectTimeout = 4000; c.readTimeout = 4000
            c.setRequestProperty("User-Agent", "HaloTV-Announcements/1")
            c.setRequestProperty("Accept", "application/json")
            try {
                val status = c.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = java.net.URL(java.net.URL(url), c.getHeaderField("Location") ?: error("redirect")).toString()
                    return@repeat
                }
                check(status == HttpURLConnection.HTTP_OK)
                val start = SystemClock.elapsedRealtime()
                val bytes = c.inputStream.use { input ->
                    val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) {
                        check(SystemClock.elapsedRealtime() - start < 8000)
                        val n = input.read(buffer); if (n < 0) break
                        check(out.size() + n <= AnnouncementPolicy.MAX_BYTES)
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
                val text = String(bytes, Charsets.UTF_8)
                return text to AnnouncementFeed.parse(text)
            } finally { c.disconnect() }
        }
        error("redirect limit")
    }
}
