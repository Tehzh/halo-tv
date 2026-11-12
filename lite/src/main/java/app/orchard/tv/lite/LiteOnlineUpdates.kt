package app.orchard.tv.lite

import android.content.Context
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection

/** Same Halo website/GitHub feeds as the full app, without Compose, services or polling timers. */
internal object LiteOnlineUpdates {
    data class Release(val version: String, val code: Long?, val urls: List<String>, val notes: String, val sha256: String?)
    enum class State { Idle, Checking, Current, Available, Failed, Downloading, Ready, DownloadFailed }
    var state = State.Idle; private set
    var release: Release? = null; private set
    var ready = false; private set
    var percent = 0; private set
    var failure = ""; private set
    val available get() = release?.takeIf { LiteReleasePolicy.newer(it.version, it.code, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()
    private var app: Context? = null
    private var lastAttempt = 0L
    private var checkedAt = 0L
    var autoDownload = false; private set
    private var homeInFront = false
    private var promptPending = false
    private var pendingGeneration = 0
    fun homeResumed() { homeInFront = true; promptDownloaded(); maybeAutoDownload() }
    fun homePaused() { homeInFront = false }
    fun chooseAutoDownload(context: Context, enabled: Boolean) {
        init(context); autoDownload = enabled
        context.getSharedPreferences("halo_lite_updates", Context.MODE_PRIVATE).edit().putBoolean("auto_download", enabled).apply()
        changed()
        if (enabled) { maybeAutoDownload(); check(context) }
    }
    private fun promptDownloaded() {
        if (promptPending && homeInFront && !Kids.active) {
            promptPending = false
            app?.let { Toast.makeText(it, R.string.halo_auto_update_ready, Toast.LENGTH_LONG).show() }
        }
    }
    private fun maybeAutoDownload() {
        val context = app ?: return
        if (!autoDownload || Kids.active || ready || state == State.Checking || state == State.Downloading) return
        val found = available ?: return
        val prefs = context.getSharedPreferences("halo_lite_updates", Context.MODE_PRIVATE)
        val key = "${found.version}:${found.code ?: 0}"
        val now = System.currentTimeMillis()
        if (prefs.getString("auto_attempt_key", null) == key && now - prefs.getLong("auto_attempt_time", 0) < 60 * 60_000L) return
        prefs.edit().putString("auto_attempt_key", key).putLong("auto_attempt_time", now).apply()
        download(context, installAfterDownload = false)
    }


    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        Announcements.init(context, "lite", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
        val prefs = context.getSharedPreferences("halo_lite_updates", Context.MODE_PRIVATE)
        autoDownload = prefs.getBoolean("auto_download", false)
        checkedAt = prefs.getLong("checked_at", 0L)
        release = runCatching {
            val saved = JSONObject(prefs.getString("offer", "").orEmpty())
            val urls = saved.getJSONArray("urls")
            Release(saved.getString("version"), saved.optLong("code", 0L).takeIf { it > 0 },
                (0 until urls.length()).map { urls.getString(it) }.filter(LiteReleasePolicy::trustedPackage),
                saved.optString("notes").take(8000), saved.optString("sha").takeIf { it.matches(Regex("[a-f0-9]{64}")) })
                .takeIf { it.urls.isNotEmpty() }
        }.getOrNull()
        if (release != null) state = if (available != null) State.Available else State.Current
        refreshUploaded(context)
    }

    fun observe(callback: () -> Unit) { listeners.add(callback) }
    fun removeObserver(callback: () -> Unit) { listeners.remove(callback) }
    private fun changed() { listeners.toList().forEach { it() } }

    fun check(context: Context, force: Boolean = false) {
        init(context)
        Announcements.check(force)
        if (state == State.Checking || state == State.Downloading) return
        val now = System.currentTimeMillis()
        if (!force && (now - checkedAt < 3 * 60 * 60_000L || now - lastAttempt < 10 * 60_000L)) return
        pendingGeneration++
        lastAttempt = now; state = State.Checking; changed()
        Thread({
            val result = runCatching { website() }.recoverCatching { github() }
            val staged = result.getOrNull()?.let { LiteUpdate.readyFor(context.applicationContext, it.version, it.code) } ?: false
            main.post {
                result.onSuccess { found ->
                    release = found; checkedAt = System.currentTimeMillis()
                    val saved = JSONObject().put("version", found.version).put("code", found.code ?: 0)
                        .put("urls", org.json.JSONArray(found.urls)).put("notes", found.notes).put("sha", found.sha256.orEmpty())
                    app!!.getSharedPreferences("halo_lite_updates", Context.MODE_PRIVATE).edit()
                        .putString("offer", saved.toString()).putLong("checked_at", checkedAt).apply()
                    if (state != State.Downloading) {
                        ready = staged
                        state = if (staged) State.Ready else if (available != null) State.Available else State.Current
                    }
                }.onFailure { if (state != State.Downloading) state = State.Failed }
                changed()
                maybeAutoDownload()
            }
        }, "HaloLite-Check").start()
    }

    fun refreshUploaded(context: Context) {
        val request = ++pendingGeneration
        val offer = release
        val found = available
        Thread({
            val pending = if (found == null) LiteUpdate.ready(context.applicationContext)
                else LiteUpdate.readyFor(context.applicationContext, found.version, found.code)
            main.post {
                // A previous file check must not overwrite a newer offer or an in-flight check.
                if (request == pendingGeneration && release == offer && state != State.Downloading && state != State.Checking) {
                    ready = pending
                    if (pending) state = State.Ready
                    changed(); if (!pending) maybeAutoDownload()
                }
            }
        }, "HaloLite-Pending").start()
    }

    fun download(context: Context, installAfterDownload: Boolean = true) {
        if (state == State.Downloading || state == State.Checking || Kids.active) return
        val found = available ?: return
        pendingGeneration++
        state = State.Downloading; percent = 0; failure = ""; changed()
        Thread({
            var reason = "download_failed"
            for (url in found.urls.distinct()) {
                reason = LiteUpdate.receiveOnline(context.applicationContext, url, found.sha256, found.code) { value ->
                    main.post { if (percent != value) { percent = value; changed() } }
                }
                if (reason == "ready" || reason in listOf("not_newer", "unsupported", "no_space", "busy")) break
            }
            main.post {
                if (reason == "ready") {
                    ready = true; state = State.Ready
                    // Installation is initiated only after the viewer explicitly chose online update.
                    if (installAfterDownload) {
                        if (!LiteUpdate.install(context.applicationContext)) failure = "installer"
                    } else { promptPending = true; promptDownloaded() }
                } else { failure = reason; state = State.DownloadFailed }
                changed()
            }
        }, "HaloLite-Download").start()
    }

    private fun website(): Release {
        val json = JSONObject(read(LiteReleasePolicy.WEBSITE + "/updates/stable.json"))
        check(json.getInt("schema") == 1)
        val version = json.getString("version").also { check(it.matches(Regex("[0-9]+(\\.[0-9]+){1,3}"))) }
        val asset = json.getJSONObject("packages").getJSONObject("lite")
        val sha = asset.getString("sha256").also { check(it.matches(Regex("[a-f0-9]{64}"))) }
        val code = asset.getLong("versionCode").also { check(it > 0) }
        val urls = arrayListOf(LiteReleasePolicy.WEBSITE + "/updates/HaloTV-$version-lite.apk")
        asset.optString("github").takeIf(LiteReleasePolicy::trustedPackage)?.let { urls.add(it) }
        return Release(version, code, urls, json.optString("notes").take(8000), sha)
    }

    private fun github(): Release {
        val json = JSONObject(read("https://api.github.com/repos/Null9797/halo-tv/releases/latest"))
        check(!json.optBoolean("draft") && !json.optBoolean("prerelease"))
        val version = Regex("^v?([0-9]+(?:\\.[0-9]+){1,3})").find(json.getString("tag_name"))?.groupValues?.get(1) ?: error("version")
        val assets = json.getJSONArray("assets")
        val file = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull {
            LiteReleasePolicy.liteName(it.optString("name")) && LiteReleasePolicy.trustedPackage(it.optString("browser_download_url"))
        } ?: error("no Lite package")
        return Release(version, null, listOf(file.getString("browser_download_url")), json.optString("body").take(8000),
            file.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[a-f0-9]{64}")) })
    }

    private fun read(url: String): String {
        val c = Net.open(url)
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "HaloTV-Lite-Updater/3.0")
        try {
            check(c.responseCode == HttpURLConnection.HTTP_OK)
            return c.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; check(out.size() + n <= 512 * 1024); out.write(buffer, 0, n) }
                String(out.toByteArray(), Charsets.UTF_8)
            }
        } finally { c.disconnect() }
    }
}
