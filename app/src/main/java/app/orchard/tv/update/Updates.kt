package app.orchard.tv.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.security.MessageDigest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import app.orchard.tv.R
import org.json.JSONObject
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.edit
import app.orchard.tv.BuildConfig
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.settings.Restricted
import app.orchard.tv.system.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Whether a newer release is out (issues #24, #76). Most owners stay on the version they installed
 * and report bugs fixed long ago. Aerial reads the latest release's tag from the
 * website (reachable in China, unlike GitHub); Control Center then names the new version and says
 * how to update. Automatic download is opt-in; installation always needs a user action. When the owner picks
 * "Download and Install" does Aerial fetch the new APK and hand it to the system installer
 * ([install]).
 *
 * Home and network events check at most every three hours; opening Control Center shortens that
 * to ten minutes. Failed checks retry while Home is in front (#912).
 */
object Updates {
    private const val TAG = "AerialUpdates"
    private const val PREFS = "updates"
    private const val RELEASE_API = "https://api.github.com/repos/Null9797/halo-tv/releases/latest"
    const val RELEASES_PAGE = "https://github.com/Null9797/halo-tv/releases"
    const val WEBSITE = "https://halo-tv.pages.dev"
    private const val SOURCE_ID = "halo-tv-website+github-v1"
    internal const val MAX_APK_BYTES = 64L * 1024 * 1024
    private const val CHECK_EVERY_MS = 3 * 60 * 60 * 1000L
    private const val RETRY_MS = 10 * 60 * 1000L

    /**
     * A source that delivers less than [STALL_BYTES] in [STALL_MS] is given up for the next one
     * (issue #390). A slow but moving download (about 8 KB/s and up) still finishes.
     */
    private const val STALL_MS = 30_000L
    private const val STALL_BYTES = 256 * 1024L
    const val CONTROL_CENTER_MAX_AGE_MS = RETRY_MS

    /** This build's version, e.g. "1.17" (the guard build's "-guard" left off). */
    val current: String = BuildConfig.VERSION_NAME.substringBefore('-')
    val edition: String get() = if (BuildConfig.FLAVOR == "guard") "Guard" else "Standard"
    val buildLabel: String get() = "Halo TV $current · $edition"
    @Volatile private var lastApkCheck = "not checked"

    /** The newer release's version, or null when this is the latest (or nothing is known yet). */
    var available by mutableStateOf<String?>(null); private set
    private var availableApkUrl: String? = null
    private var mirrorApkUrl: String? = null
    private var expectedSha: String? = null
    private var expectedCode: Long? = null

    /**
     * The latest Halo TV GitHub release notes; empty when no newer release or no body is available.
     */
    var notes by mutableStateOf<List<ReleaseNotes>>(emptyList()); private set

    /** Where an install started from Control Center has got to. */
    var install by mutableStateOf<Install>(Install.Idle); private set

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var lastAttempt = 0L
    var autoDownload by mutableStateOf(false); private set
    private var autoPromptPending = false

    fun chooseAutoDownload(enabled: Boolean) {
        autoDownload = enabled
        prefs.edit { putBoolean("auto_download", enabled) }
        if (enabled) { maybeAutoDownload(); checkIfStale() }
    }

    /** Uses the existing check lifecycle. Never invokes an installer without a user click. */
    private fun maybeAutoDownload() {
        if (!started || !autoDownload || Restricted.active || (install != Install.Idle && install != Install.Failed(Failure.Download)) || awaiting != null) return
        val version = available ?: return
        val url = availableApkUrl ?: return
        val release = Release(version, url, "", mirrorApkUrl, expectedSha, expectedCode)
        val key = "$version:${expectedCode ?: 0}"
        val now = System.currentTimeMillis()
        if (prefs.getString("auto_attempt_key", null) == key && now - prefs.getLong("auto_attempt_time", 0) < 60 * 60_000L) return
        if (!canInstall(appContext) || LegacyUpdateStorage.needsPermission(appContext)) return
        prefs.edit { putString("auto_attempt_key", key); putLong("auto_attempt_time", now) }
        install = Install.Downloading(0f)
        scope.launch {
            val file = apkFile(appContext)
            val result = withContext(Dispatchers.IO) {
                runCatching { validDownload(appContext, file, release) ?: downloadFrom(appContext, file, release) }.getOrNull()
            }
            val verified = result != null && withContext(Dispatchers.IO) { validDownload(appContext, file, release) != null }
            if (!verified) install = Install.Failed(Failure.Download)
            else {
                install = Install.Ready
                autoPromptPending = true
                promptDownloaded()
            }
        }
    }

    private fun promptDownloaded() {
        if (autoPromptPending && homeInFront && !Restricted.active) {
            autoPromptPending = false
            Toast.makeText(appContext, R.string.halo_auto_update_ready, Toast.LENGTH_LONG).show()
        }
    }


    enum class Check { Idle, Checking, Success, Failed }

    var checkState by mutableStateOf(Check.Idle)
        private set

    fun diagnostics(): String = if (!started) "not initialized" else
        "source=$SOURCE_ID current=$current latest=${prefs.getString("latest", null) ?: "-"} " +
            "checked_at=${prefs.getLong("checked_at", 0)} available=${available ?: "-"} " +
            "edition=$edition installed_signer=${ApkSigners.installed(appContext)} apk_check=$lastApkCheck"

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        app.orchard.tv.announcements.Announcements.init(context, BuildConfig.FLAVOR, current, BuildConfig.VERSION_CODE.toLong())
        autoDownload = prefs.getBoolean("auto_download", false)
        // Do not keep a version cached from an older update repository.
        if (prefs.getString("source", null) != SOURCE_ID) {
            prefs.edit {
                remove("latest"); remove("apk_url"); remove("checked_at"); remove("release_body")
                remove("mirror_url"); remove("apk_sha"); remove("apk_code")
                putString("source", SOURCE_ID)
            }
        }
        availableApkUrl = prefs.getString("apk_url", null)
        mirrorApkUrl = prefs.getString("mirror_url", null)
        expectedSha = prefs.getString("apk_sha", null)
        expectedCode = prefs.getLong("apk_code", 0).takeIf { it > 0 }
        available = prefs.getString("latest", null)?.takeIf { newerRelease(it, expectedCode) }
        loadNotes()
        val cachedRelease = available?.let { v -> availableApkUrl?.let { Release(v, it, "", mirrorApkUrl, expectedSha, expectedCode) } }
        if (cachedRelease != null) scope.launch {
            val valid = withContext(Dispatchers.IO) { runCatching { validDownload(appContext, apkFile(appContext), cachedRelease) != null }.getOrDefault(false) }
            if (valid && install == Install.Idle && available == cachedRelease.version && expectedCode == cachedRelease.code) {
                install = Install.Ready
                autoPromptPending = autoDownload
                promptDownloaded()
            }
        }
        // A download left from an install that has since finished or been given up.
        scope.launch(Dispatchers.IO) {
            apkFile(context).takeIf { System.currentTimeMillis() - it.lastModified() > 7 * 24 * 60 * 60 * 1000L }?.delete()
            File(context.cacheDir, "phone-update").listFiles()?.forEach {
                if (System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000L) it.delete()
            }
        }
    }

    /**
     * [force]: now, however recently it last looked (ConfigReceiver's check_update). Opening Control
     * Center asks with a shorter [maxAgeMs]: that is where the viewer looks for a new version, and a
     * release out an hour ago was not shown there for hours more (issue #301).
     */
    fun checkIfStale(force: Boolean = false, maxAgeMs: Long = CHECK_EVERY_MS, networkCameUp: Boolean = false) {
        app.orchard.tv.announcements.Announcements.check(force, maxAgeMs, networkCameUp)
        if (!started || checkState == Check.Checking) return
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked_at", 0) < maxAgeMs) return
        if (!force && !networkCameUp && now - lastAttempt < RETRY_MS) return
        lastAttempt = now
        checkState = Check.Checking
        scope.launch {
            val latest = withContext(Dispatchers.IO) {
                runCatching { readWebsiteRelease() }.recoverCatching { readLatestRelease() }
                    .onFailure { Log.w(TAG, "could not check for a new version", it) }
                    .getOrNull()
            }
            if (latest == null) {
                checkState = Check.Failed
                return@launch
            }
            prefs.edit {
                putString("latest", latest.version)
                putString("apk_url", latest.apkUrl)
                putString("release_body", latest.body)
                putString("mirror_url", latest.mirrorUrl)
                putString("apk_sha", latest.sha256)
                putLong("apk_code", latest.code ?: 0L)
                putLong("checked_at", System.currentTimeMillis())
            }
            if (install == Install.Ready && (available != latest.version || expectedCode != latest.code)) install = Install.Idle
            availableApkUrl = latest.apkUrl
            mirrorApkUrl = latest.mirrorUrl
            expectedSha = latest.sha256
            expectedCode = latest.code
            available = latest.version.takeIf { newerRelease(it, latest.code) }
            loadNotes()
            checkState = Check.Success
            maybeAutoDownload()
        }
    }

    /** Use Halo TV's release description, never the upstream Aerial update feed. */
    fun loadNotes() {
        if (!started) return
        val version = available ?: run { notes = emptyList(); return }
        val body = prefs.getString("release_body", "").orEmpty()
        val lines = body.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("---") }
            .map { it.removePrefix("- ").removePrefix("* ").replace("**", "").trim() }
            .filter { it.isNotBlank() }.take(30).toList()
        notes = if (lines.isEmpty()) emptyList() else listOf(ReleaseNotes(version, lines))
    }

    /** Whether version [a] comes after [b], comparing dotted numbers ("1.10" after "1.9"). */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = Regex("\\d+").findAll(b).mapNotNull { it.value.toIntOrNull() }.toList()
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun newerRelease(version: String, code: Long?) =
        if (code != null) code > BuildConfig.VERSION_CODE && !newer(current, version) else newer(version, current)

    private data class Release(
        val version: String,
        val apkUrl: String,
        val body: String,
        val mirrorUrl: String? = null,
        val sha256: String? = null,
        val code: Long? = null,
    )

    private fun readWebsiteRelease(): Release {
        val json = JSONObject(read("$WEBSITE/updates/stable.json"))
        check(json.getInt("schema") == 1)
        val version = json.getString("version")
        check(version.matches(Regex("[0-9]+(\\.[0-9]+){1,3}")))
        val asset = json.getJSONObject("packages").getJSONObject(BuildConfig.FLAVOR)
        val file = "HaloTV-$version-${BuildConfig.FLAVOR}.apk"
        val mirror = asset.optString("github").takeIf {
            it.startsWith("$RELEASES_PAGE/download/") && it.endsWith("/$file")
        }
        val sha = asset.getString("sha256")
        check(sha.matches(Regex("[a-f0-9]{64}")))
        val code = asset.getLong("versionCode").also { check(it > 0) }
        return Release(version, "$WEBSITE/updates/$file", json.optString("notes").take(16_000), mirror, sha, code)
    }

    /** Read only the latest stable release and its APK for this installed flavor. */
    private fun readLatestRelease(): Release {
        val json = JSONObject(read(RELEASE_API))
        check(!json.optBoolean("prerelease", false) && !json.optBoolean("draft", false)) { "no stable release" }
        val tag = json.getString("tag_name").removePrefix("v")
        val version = Regex("^\\d+(?:\\.\\d+)*").find(tag)?.value ?: error("invalid release tag: $tag")
        val suffix = "-${BuildConfig.FLAVOR}.apk"
        val assets = json.getJSONArray("assets")
        val candidates = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter {
                val name = it.optString("name")
                (name.startsWith("HaloTV-") || name.startsWith("halotv-") || name.startsWith("aerial-universal-")) && name.endsWith(suffix)
            }
        val asset = candidates.firstOrNull() ?: error("release has no APK for ${BuildConfig.FLAVOR}")
        val url = asset.optString("browser_download_url").takeIf { it.startsWith("https://github.com/Null9797/halo-tv/releases/download/") }
            ?: error("invalid APK URL")
        return Release(version, url, json.optString("body", "").take(16_000))
    }

    private fun read(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        LegacyUpdateTls.configure(appContext, c)
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Halo-TV-Updater")
        return try {
            c.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                buildString {
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        check(length + count <= 512 * 1024) { "Update manifest too large" }
                        append(buffer, 0, count)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    // ---- Installing the new version from Control Center ----

    sealed interface Install {
        data object Idle : Install
        data class Downloading(val fraction: Float) : Install
        /** A session was submitted; neither a result nor a visible confirmation is known yet. */
        data object Ready : Install
        data object Waiting : Install
        /** The system installer is open; it asks the owner and replaces Aerial, which restarts. */
        data object Opened : Install
        /** Aerial may not install apps yet; the TV's setting for it is open. */
        data object NeedsPermission : Install
        /** Android 6 firmware requires storage access before the APK can be saved. */
        data object NeedsStoragePermission : Install
        data class Failed(val reason: Failure) : Install
    }

    enum class Failure {
        /** Neither the website nor GitHub gave a complete APK. */
        Download,
        /** The TV lets no app hand an APK to its installer, or has no installer to hand it to. */
        Blocked,
        /** This copy is not the official package or not signed with the official key. */
        NotOfficial,
        /** The downloaded APK is no newer than this one (the website not yet updated). */
        NotNewer,
    }

    /** The official package. Copies renamed for one firmware (OPPO, see docs/devices) cannot update from the release. */
    private const val PACKAGE = "app.orchard.tv"

    /** Whether this copy can update itself from the release at all. */
    fun canInstall(context: Context): Boolean = context.packageName == PACKAGE

    /**
     * Downloads this flavor's APK of the latest release and installs it. Android 9 and later, a
     * PackageInstaller session first (installBySession), as only a session can carry the dex
     * metadata that has the system compile Aerial as it installs. Where the session fails, the system
     * installer by ACTION_VIEW, as before Android 9: TCL's verifier (docs/devices/tcl-mt5879-cn.md)
     * passes only installs that go through com.android.packageinstaller, as Dangbei's do, and an
     * ACTION_VIEW on the APK makes the installer itself the one installing. The APK must be this package, signed
     * with this copy's key and no older, or the installer would only fail with a less clear message.
     */
    fun install(context: Context) = beginInstall(context, null)

    internal fun installFromPhone(context: Context, upload: File) {
        if (!Restricted.active) beginInstall(context, upload)
    }

    private fun beginInstall(context: Context, upload: File?) {
        if (install is Install.Downloading || install == Install.Opened) return
        // A second press while the first session's confirmation is awaited (#1004: two sessions).
        if (awaiting != null) return
        val app = context.applicationContext
        if (!canInstall(app)) { install = Install.Failed(Failure.NotOfficial); return }
        if (LegacyUpdateStorage.needsPermission(app)) {
            install = Install.NeedsStoragePermission
            LegacyUpdateStorage.requestPermission(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            // Xiaomi MiTV-MFTR0 has this page; TCL MT5879 does not (tools/install.sh grants it there).
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(settings) }.isSuccess) {
                install = Install.NeedsPermission
                return
            }
            // No page to allow it on (Xiaomi MiTV-MFTP0, Android 9, has only Xiaomi's own settings:
            // "this TV does not let apps install apps", issue #284). Firmware installers often let
            // it through or ask themselves, so the download goes ahead and the installer decides.
            Log.i(TAG, "not allowed to install and no page to allow it on; trying the installer anyway")
        }
        // Take ownership before leaving Main so closing the QR cannot remove a confirmed upload.
        val owned = upload?.let {
            val target = File(it.parentFile, "${it.nameWithoutExtension}-confirmed.apk")
            if (!it.renameTo(target)) {
                install = Install.Failed(Failure.Download)
                return
            }
            target
        }
        val requested = available?.let { version ->
            availableApkUrl?.let { Release(version, it, "", mirrorApkUrl, expectedSha, expectedCode) }
        }
        install = Install.Downloading(0f)
        scope.launch {
            val file = apkFile(app)
            val ready = withContext(Dispatchers.IO) {
                // A download kept from a moment ago, when the owner backed out of the installer.
                if (owned != null) {
                    try {
                        if (validatePhoneApk(app, owned).error != null) null else runCatching {
                            check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                            owned.copyTo(file, overwrite = true)
                            if (Build.VERSION.SDK_INT < 24) file.setReadable(true, false)
                            verify(app, file)
                        }.getOrNull()
                    } finally {
                        owned.delete()
                    }
                } else {
                    requested?.let { validDownload(app, file, it) } ?: downloadFrom(app, file, requested)
                }
            }
            val failure = when {
                ready == null -> Failure.Download
                !sameSigner(app, ready) -> Failure.NotOfficial
                versionCode(ready) < BuildConfig.VERSION_CODE -> Failure.NotNewer
                else -> null
            }
            if (failure != null) {
                Log.w(TAG, "not installing: $failure")
                if (failure != Failure.Download) file.delete()
                install = Install.Failed(failure)
                return@launch
            }
            val opened = if (Build.VERSION.SDK_INT >= 28) installBySession(app, file) else openInstaller(context, app, file)
            if (!opened) install = Install.Failed(Failure.Blocked)
        }
    }

    /**
     * From Android 13 the installer's ACTION_VIEW records the APK as a local file (packageSource 3),
     * and Android then restricts the app's settings (ACCESS_RESTRICTED_SETTINGS deny; seen on the
     * Android 13 TV emulator). On TCL T966D5 (Android 14) Home Guard was off after every update made
     * this way, and stayed on after one pushed by 多屏互动 (issue #723). A session that says where
     * the APK came from is not restricted; the system still asks the owner to confirm. Where the
     * firmware turns the session down, the installer's ACTION_VIEW as before (UpdateResult).
     *
     * The session also carries the new APK's dex metadata (assets/aerial-dm, see app/build.gradle.kts)
     * as aerial.dm, from Android 9: the system then compiles Aerial with its Baseline Profile as it
     * installs (Xiaomi MiTV-MFTR0: status speed-profile, reason install-dm). Without it the update ran
     * uncompiled until the next one, its main thread about twice as slow (docs/PERF-2026-10-30.md).
     * Should a firmware refuse the metadata, the APK alone once more ([withDm]).
     */
    @RequiresApi(28)
    private fun installBySession(app: Context, file: File, withDm: Boolean = true): Boolean = runCatching {
        val dm = if (withDm) dexMetadata(file) else null
        val id = commitSession(app, file, dm, Intent(app, UpdateResult::class.java).putExtra(EXTRA_WITH_DM, dm != null))
        Log.i(TAG, "install session $id committed for ${file.length()} bytes, dex metadata ${dm?.size ?: "none"}")
        DiagLog.i("update: session $id, dex metadata ${dm?.size ?: "none"}")
        // Hisense VIDAA_TV / MT9655 (Android 14, #1162) also accepts sessions without answering.
        // Silent updates need time to install and compile, not the short confirmation deadline.
        val timeout = if (mayInstallSilently(app)) SILENT_INSTALL_TIMEOUT_MS else ANSWER_TIMEOUT_MS
        awaitSession(app, id, timeout, "no answer from session $id")
        true
    }.getOrElse {
        if (withDm) {
            Log.w(TAG, "install session with dex metadata failed; without it", it)
            installBySession(app, file, withDm = false)
        } else {
            Log.w(TAG, "no install session; opening the installer", it)
            openInstaller(app, app, file)
        }
    }

    /**
     * The running APK over itself with its own dex metadata (SelfCompile): no download, the same
     * version. Without the metadata it would be pointless, so no fallback: a failure is only logged.
     * [auto]: nobody asked, so a confirmation page is not put in front of the app being watched.
     */
    @RequiresApi(28)
    internal fun reinstallSelf(app: Context, auto: Boolean) {
        val apk = File(app.applicationInfo.sourceDir)
        val dm = dexMetadata(apk) ?: return
        runCatching {
            val result = Intent(app, UpdateResult::class.java).putExtra(EXTRA_WITH_DM, true)
                .putExtra(EXTRA_SELF, if (auto) SELF_AUTO else SELF_ASKED)
            val id = commitSession(app, apk, dm, result)
            Log.i(TAG, "self compile: session $id committed, dex metadata ${dm.size}")
        }.onFailure {
            Log.w(TAG, "self compile: no session", it)
            DiagLog.i("self compile: no session (${it.javaClass.simpleName}: ${it.message})")
        }
    }

    /** Writes [file] (and [dm] beside it) into a new install session and commits it; returns its id. */
    @RequiresApi(28)
    private fun commitSession(app: Context, file: File, dm: ByteArray?, result: Intent): Int {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
            // Android 12 and later let an app update itself without the confirmation page, which some
            // firmwares do not have (Xiaomi MiTV-MFTR0: no CONFIRM_INSTALL activity, so the session
            // gave way to the installer's ACTION_VIEW and its dex metadata was lost). The owner has
            // just picked "Download and Install". Where the system still wants a confirmation, it
            // answers STATUS_PENDING_USER_ACTION as before.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("aerial.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                if (dm != null) session.openWrite("aerial.dm", 0, dm.size.toLong()).use { out ->
                    out.write(dm)
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                GuardSetup.noteInstallerOpened(app)
                session.commit(PendingIntent.getBroadcast(app, id, result, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
        return id
    }

    /** This APK's own dex metadata for this Android, or null (SelfCompile). */
    internal fun ownDexMetadata(context: Context): ByteArray? = dexMetadata(File(context.applicationInfo.sourceDir))

    /**
     * The dex metadata the build put in [apk] for this Android (28.dm for 9 to 11, 31.dm from 12),
     * or null: an APK from before 1.72, or one that cannot be read.
     */
    private fun dexMetadata(apk: File): ByteArray? = runCatching {
        val name = if (Build.VERSION.SDK_INT >= 31) "assets/aerial-dm/31.dm" else "assets/aerial-dm/28.dm"
        java.util.zip.ZipFile(apk).use { zip -> zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
    }.getOrNull()

    private const val EXTRA_WITH_DM = "app.orchard.tv.update.WITH_DM"
    private const val EXTRA_SELF = "app.orchard.tv.update.SELF"
    private const val SELF_ASKED = 1
    private const val SELF_AUTO = 2

    /**
     * The update's session while its answer or confirmation page is awaited, else null. Hisense VIDAA_TV
     * (Android 9, Amlogic T963, issue #1004) took the session and then showed nothing: no answer
     * in the log, no confirmation page, Home never paused. Before 1.72 the installer's ACTION_VIEW
     * worked there. So a session whose page does not come up in time is abandoned for it.
     */
    private var awaiting: Awaiting? = null
    private class Awaiting(val id: Int, val confirmation: Boolean, val showConfirmation: Runnable?) {
        var homeLeft = false
        var timedOut = false
        lateinit var check: Runnable
    }
    private var homeInFront = false
    private val main by lazy { Handler(Looper.getMainLooper()) }
    /** Sessions given up on: their answers, should they still come, are ignored. */
    private val abandoned = mutableSetOf<Int>()

    /** From the commit to the system's answer; it comes within a second on the TVs at hand. */
    private const val ANSWER_TIMEOUT_MS = 8_000L
    /** Android 12+ may compile and replace the app without any intermediate answer or page. */
    private const val SILENT_INSTALL_TIMEOUT_MS = 120_000L
    /**
     * Whether Android may install the update without asking: from 12, and only for the app that
     * installed Aerial last (USER_ACTION_NOT_REQUIRED). Installed by adb (#1162's Hisense: none) or
     * by the system installer, it always asks, and an answer is due as soon as before 12.
     */
    private fun mayInstallSilently(app: Context): Boolean = Build.VERSION.SDK_INT >= 31 && runCatching {
        app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName == app.packageName
    }.getOrDefault(true)

    /** From starting the confirmation page to Home pausing beneath it. */
    private const val CONFIRM_TIMEOUT_MS = 4_000L

    /** Wait for an answer, or for a requested confirmation page to pause Home. */
    private fun awaitSession(
        app: Context, id: Int, ms: Long, why: String,
        confirmation: Boolean = false, showConfirmation: Runnable? = null,
    ) {
        awaiting?.let { main.removeCallbacks(it.check) }
        val a = Awaiting(id, confirmation, showConfirmation)
        a.check = Runnable {
            if (awaiting !== a || (a.confirmation && a.homeLeft)) return@Runnable
            a.timedOut = true
            // Do not open an installer over another app or while the TV is asleep.
            if (homeInFront) giveUpOnSession(app, id, why)
        }
        awaiting = a
        install = Install.Waiting
        // A confirmation requested while away is opened on resume; its timer starts then.
        if (showConfirmation == null) main.postDelayed(a.check, ms)
    }

    private fun stopAwaiting(id: Int) {
        val a = awaiting?.takeIf { it.id == id } ?: return
        main.removeCallbacks(a.check)
        awaiting = null
    }

    private fun giveUpOnSession(app: Context, id: Int, why: String) {
        stopAwaiting(id)
        abandoned += id
        runCatching { app.packageManager.packageInstaller.abandonSession(id) }
        Log.w(TAG, "$why; abandoned, opening the installer")
        DiagLog.i("update: $why; abandoned, installer")
        val file = apkFile(app)
        if (!file.isFile || !openInstaller(app, app, file)) install = Install.Failed(Failure.Blocked)
    }

    /** The session's answer (UpdateResult): the owner's confirmation, or the installer instead. */
    internal fun onSessionResult(context: Context, intent: Intent) {
        val app = context.applicationContext
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val self = intent.getIntExtra(EXTRA_SELF, 0)
        if (self != 0) return onSelfResult(app, intent, status, auto = self == SELF_AUTO)
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (id in abandoned) {
            Log.i(TAG, "session $id answered ($status) after it was given up")
            return
        }
        stopAwaiting(id)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) return giveUpOnSession(app, id, "session $id asks to confirm but has no page")
                if (homeInFront) openConfirmation(app, id, confirm)
                else awaitSession(
                    app, id, CONFIRM_TIMEOUT_MS, "confirmation page of session $id did not come up",
                    confirmation = true, showConfirmation = Runnable { openConfirmation(app, id, confirm) },
                )
            }
            PackageInstaller.STATUS_SUCCESS -> install = Install.Idle // normally this process is replaced
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.i(TAG, "update not confirmed")
                install = Install.Idle
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val file = apkFile(app)
                // A firmware that turns the dex metadata down (INSTALL_FAILED_BAD_DEX_METADATA):
                // the APK alone in a new session.
                if (intent.getBooleanExtra(EXTRA_WITH_DM, false) && message.orEmpty().contains("DEX_METADATA", ignoreCase = true) &&
                    Build.VERSION.SDK_INT >= 28 && file.isFile
                ) {
                    Log.w(TAG, "install session refused the dex metadata ($message); without it")
                    if (installBySession(app, file, withDm = false)) return
                }
                Log.w(TAG, "install session failed ($status, $message); opening the installer")
                DiagLog.i("update: session failed ($status, $message); installer")
                if (!file.isFile || !openInstaller(app, app, file)) install = Install.Failed(Failure.Blocked)
            }
        }
    }

    private fun openConfirmation(app: Context, id: Int, confirm: Intent) {
        runCatching { app.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onSuccess {
            DiagLog.i("update: session $id asks to confirm; page started")
            awaitSession(app, id, CONFIRM_TIMEOUT_MS, "confirmation page of session $id did not come up", confirmation = true)
        }.onFailure {
            // Some Android 13+ firmware has no CONFIRM_INSTALL page but accepts ACTION_VIEW (#827).
            Log.w(TAG, "could not ask to confirm the update; opening the installer", it)
            giveUpOnSession(app, id, "session $id has no confirmation page (${it.javaClass.simpleName})")
        }
    }

    /** The answer to [reinstallSelf]: never the installer by ACTION_VIEW, which would carry no metadata. */
    private fun onSelfResult(app: Context, intent: Intent, status: Int, auto: Boolean) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        // Arrives in the new process, once the replace is done.
        if (status == PackageInstaller.STATUS_SUCCESS) {
            DiagLog.i("self compile: installed")
            return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && !auto) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null && runCatching { app.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && id >= 0) runCatching { app.packageManager.packageInstaller.abandonSession(id) }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.w(TAG, "self compile: not installed ($status, $message)")
        DiagLog.i("self compile: not installed ($status, $message)")
    }

    /** Resume failed checks; if the installer closed without replacing Aerial, reset its button. */
    fun onHomeResumed() {
        homeInFront = true
        promptDownloaded()
        maybeAutoDownload()
        if (install == Install.Opened) install = Install.Idle
        awaiting?.let {
            // Only returning from a requested confirmation releases the session's button.
            // Visiting another app while awaiting an answer must not forget the update.
            if (it.showConfirmation != null) it.showConfirmation.run()
            else if (it.confirmation && it.homeLeft) stopAwaiting(it.id)
            else if (it.timedOut) it.check.run()
        }
    }

    fun onHomePaused() {
        homeInFront = false
        awaiting?.takeIf { it.confirmation }?.let {
            it.homeLeft = true
            install = Install.Opened
        }
    }

    /** Back to the button, when the update page is left. */
    fun resetInstall() {
        if (awaiting == null && install !is Install.Downloading && install != Install.Ready) install = Install.Idle
    }

    private fun apkFile(context: Context): File {
        // Android 5 and 6 installers read a file:// URI, so there the APK sits where they can read it.
        val dir = if (Build.VERSION.SDK_INT >= 24) context.cacheDir else context.externalCacheDir ?: context.cacheDir
        return File(File(dir, "updates"), "aerial.apk")
    }

    /**
     * For testing the whole update on a TV before a release (ConfigReceiver's update_test): the APK
     * pushed to Android/data/app.orchard.tv/files/update-test.apk stands in for the download.
     */
    @Volatile var testApk: File? = null

    /** The website first (reachable in China), then GitHub. Returns the APK's info once it parses. */
    private suspend fun downloadFrom(context: Context, file: File, release: Release?): PackageInfo? {
        testApk?.takeIf { it.isFile }?.let { test ->
            file.parentFile?.mkdirs()
            test.copyTo(file, overwrite = true)
            if (Build.VERSION.SDK_INT < 24) file.setReadable(true, false)
            return verify(context, file)
        }
        if (release == null) return null
        for (url in listOfNotNull(release.apkUrl, release.mirrorUrl).distinct()) {
            val verified = runCatching {
                download(url, file)
                validDownload(context, file, release) ?: error("APK validation failed")
            }.onFailure {
                Log.w(TAG, "update download failed from ${URL(url).host}", it)
                file.delete()
            }.getOrNull()
            if (verified != null) return verified
        }
        return null
    }

    private suspend fun download(url: String, file: File) {
        val directory = checkNotNull(file.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create update directory: $directory" }
        check(directory.canWrite()) { "Cannot write update directory: $directory" }
        val part = File(directory, file.name + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        LegacyUpdateTls.configure(appContext, c)
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        // A throttled route can keep the TLS handshake up and deliver a byte now and then, so no
        // read() ever blocks long enough to time out and the progress bar sits at 0% for good
        // (issue #390, FFALCON/TCL on China Unicom). So the bytes are counted, not each read timed.
        var windowAt = SystemClock.elapsedRealtime()
        var windowFrom = 0L
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${c.responseCode}")
            val total = c.contentLength.toLong()
            check(total <= MAX_APK_BYTES) { "APK too large" }
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        check(done <= MAX_APK_BYTES) { "APK too large" }
                        out.write(buf, 0, n)
                        val now = SystemClock.elapsedRealtime()
                        if (now - windowAt >= STALL_MS) {
                            if (done - windowFrom < STALL_BYTES) error("stalled: ${done - windowFrom} bytes in ${(now - windowAt) / 1000} s")
                            windowAt = now
                            windowFrom = done
                        }
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != shown) {
                            shown = percent
                            withContext(Dispatchers.Main) { install = Install.Downloading(percent / 100f) }
                        }
                    }
                }
            }
            if (total > 0 && done != total) error("got $done of $total bytes")
            if (!part.renameTo(file)) error("could not keep the download")
            // The installer on Android 5 and 6 opens the file itself.
            if (Build.VERSION.SDK_INT < 24) file.setReadable(true, false)
        } finally {
            c.disconnect()
            part.delete()
        }
    }

    private fun validDownload(context: Context, file: File, release: Release): PackageInfo? {
        val info = verify(context, file) ?: return null
        if (validatePhoneApk(context, file, info).error != null ||
            info.versionName?.substringBefore('-') != release.version ||
            release.code?.let { versionCode(info) != it } == true ||
            release.sha256?.let { sha256(file) != it } == true) return null
        return info
    }

    internal data class ApkCheck(val version: String? = null, val error: String? = null)

    internal fun validatePhoneApk(context: Context, file: File): ApkCheck =
        validatePhoneApk(context, file, verify(context, file))

    private fun validatePhoneApk(context: Context, file: File, info: PackageInfo?): ApkCheck {
        if (info == null || !file.isFile) return ApkCheck(error = "wrong_app")
        val incoming = ApkSigners.current(info)
        val installed = ApkSigners.installed(context)
        val signatureError = ApkSigners.error(incoming, installed)
        val variant = info.applicationInfo?.metaData?.getString("app.halotv.update.variant")
        lastApkCheck = "incoming=$incoming installed=$installed variant=$variant code=${versionCode(info)} result=${signatureError ?: "signatures match"}"
        Log.i(TAG, "APK validation: $lastApkCheck")
        if (signatureError != null) return ApkCheck(error = signatureError)
        if (variant != BuildConfig.FLAVOR) return ApkCheck(error = "wrong_variant")
        if (versionCode(info) <= BuildConfig.VERSION_CODE) return ApkCheck(error = "not_newer")
        if (Build.VERSION.SDK_INT >= 24 && (info.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) {
            return ApkCheck(error = "unsupported")
        }
        val version = info.versionName?.takeIf { it.matches(Regex("[0-9]+(\\.[0-9]+){1,3}(-[a-zA-Z0-9.-]+)?")) }
            ?: return ApkCheck(error = "wrong_app")
        return ApkCheck(version)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(Locale.ROOT, it) }

    /** The APK's package info when it parses as this package, else null. */
    @Suppress("DEPRECATION")
    private fun verify(context: Context, file: File): PackageInfo? {
        if (!file.isFile) return null
        val info = ApkSigners.read { flags ->
            context.packageManager.getPackageArchiveInfo(file.path, flags or PackageManager.GET_META_DATA)
        } ?: return null
        return info.takeIf { it.packageName == context.packageName }
    }

    /** Missing signing data is never permission to install. */
    private fun sameSigner(context: Context, apk: PackageInfo): Boolean {
        val incoming = ApkSigners.current(apk) ?: return false
        return incoming == ApkSigners.installed(context)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun openInstaller(context: Context, app: Context, file: File): Boolean {
        val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        else Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            GuardSetup.noteInstallerOpened(app)
            context.startActivity(intent)
            install = Install.Opened
            Log.i(TAG, "installer opened for ${file.length()} bytes")
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no installer", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "installer refused", e)
            false
        }
    }
}
