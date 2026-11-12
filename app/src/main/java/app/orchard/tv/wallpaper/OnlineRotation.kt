package app.orchard.tv.wallpaper

import app.orchard.tv.system.CompatibleHttp

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Best-effort wallpaper rotation while the launcher process is alive; checks again on restart. */
object OnlineRotation {
    private const val BASE = "https://ygn-bing-wallpaper.pages.dev/"
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by lazy { app.getSharedPreferences("online_wallpaper_rotation", Context.MODE_PRIVATE) }
    var bingMinutes by mutableIntStateOf(0); private set
    var videoMinutes by mutableIntStateOf(0); private set
    var bingStatus by mutableStateOf(BingRotationStatus.Idle); private set
    private var generation = 0
    private var running: Job? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true; app = context.applicationContext
        CompatibleHttp.init(app)
        bingMinutes = prefs.getInt("bing_minutes", 0).coerceIn(-1, 10080)
        videoMinutes = prefs.getInt("video_minutes", 0).coerceIn(-1, 10080)
        scope.launch {
            while (true) {
                try { checkDue() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.w("OnlineRotation", "rotation check failed", e) }
                delay(30_000)
            }
        }
    }

    fun chooseBingMinutes(minutes: Int, category: String) {
        bingMinutes = minutes.coerceIn(-1, 10080); generation++; running?.cancel()
        val target = Wallpapers.selectedId
        bingStatus = if (bingMinutes != 0) BingRotationStatus.Loading else BingRotationStatus.Idle
        prefs.edit {
            putInt("bing_minutes", bingMinutes); putString("bing_category", category)
            if (bingMinutes != 0) putString("bing_target", target)
            putLong("bing_next", if (bingMinutes != 0) System.currentTimeMillis() else 0L)
        }
        if (bingMinutes != 0) scope.launch { checkDue(applyBingNow = true) }
    }
    fun chooseVideoMinutes(minutes: Int) {
        videoMinutes = minutes.coerceIn(-1, 10080); generation++; running?.cancel()
        if (bingStatus == BingRotationStatus.Loading) bingStatus = BingRotationStatus.Idle
        prefs.edit { putInt("video_minutes", videoMinutes); putLong("video_next", next(videoMinutes)) }
        val films = Aerials.all(app)
        if (videoMinutes != 0 && films.isNotEmpty()) {
            val current = if (Wallpapers.selectedId == Wallpapers.LIVE) films.indexOfFirst { it.id == Wallpapers.selectedAerialId } else -1
            Wallpapers.chooseAerial(films[randomOnlineIndex(current, films.size)].id)
        } else if (videoMinutes == 0 && Wallpapers.selectedId == Wallpapers.LIVE && Wallpapers.selectedAerialId == null) {
            films.firstOrNull()?.let { Wallpapers.chooseAerial(it.id) }
        }
    }
    /** Called only after an explicit user download succeeds. */
    fun markBingApplied(id: String, category: String, date: String) {
        generation++; running?.cancel()
        prefs.edit { putString("bing_target", id); putString("bing_category", category); putString("bing_last_date", date); putLong("bing_next", next(bingMinutes)) }
    }
    fun isBingGenerationCurrent(expected: Int): Boolean = initialized && expected == generation && bingMinutes != 0
    fun markVideoApplied() { prefs.edit { putLong("video_next", next(videoMinutes)) } }
    private fun next(minutes: Int) = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L

    /** Set only when launching an external app, not when opening a Compose panel. */
    fun noteExternalAppOpened() { if (initialized) prefs.edit { putBoolean("return_pending", true) } }
    fun cancelExternalReturn() { if (initialized) prefs.edit { putBoolean("return_pending", false) } }
    fun onHomeReturned() {
        if (!initialized || !prefs.getBoolean("return_pending", false)) return
        cancelExternalReturn()
        scope.launch { checkDue(returnedHome = true) }
    }

    private suspend fun checkDue(returnedHome: Boolean = false, applyBingNow: Boolean = false) {
        val now = System.currentTimeMillis()
        val target = prefs.getString("bing_target", null)
        val nextBing = prefs.getLong("bing_next", 0)
        if (rotationDue(bingMinutes, nextBing, now, returnedHome, applyBingNow) && target != null && Wallpapers.selectedId == target && running?.isActive != true) {
            val expectedGeneration = generation
            val category = prefs.getString("bing_category", "").orEmpty()
            val lastDate = prefs.getString("bing_last_date", "").orEmpty()
            bingStatus = BingRotationStatus.Loading
            running = scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { fetchNext(category, lastDate) }
                        .onFailure { Log.w("OnlineRotation", "unable to obtain a random image", it) }.getOrNull()
                }
                if (expectedGeneration != generation || bingMinutes == 0 || Wallpapers.selectedId != target) {
                    if (expectedGeneration == generation) bingStatus = BingRotationStatus.Idle
                    return@launch
                }
                if (result != null && Wallpapers.storeBingRotation(result.first, result.second, target, expectedGeneration)) {
                    prefs.edit { putString("bing_target", Wallpapers.BING_ROTATION); putString("bing_last_date", result.third); putLong("bing_next", next(bingMinutes)) }
                    bingStatus = BingRotationStatus.Started
                } else if (expectedGeneration == generation && bingMinutes != 0 && Wallpapers.selectedId == target) {
                    bingStatus = BingRotationStatus.Failed
                    prefs.edit { putLong("bing_next", System.currentTimeMillis() + 5 * 60_000L) }
                }
            }
        }
        val dueVideo = prefs.getLong("video_next", 0)
        if (rotationDue(videoMinutes, dueVideo, now, returnedHome) && Wallpapers.selectedId == Wallpapers.LIVE) {
            val films = Aerials.all(app)
            if (films.isNotEmpty()) {
                val current = films.indexOfFirst { it.id == Wallpapers.selectedAerialId }
                Wallpapers.chooseAerial(films[randomOnlineIndex(current, films.size)].id)
            }
            prefs.edit { putLong("video_next", next(videoMinutes)) }
        }
    }

    /** Independent saver fetch: does not select a desktop wallpaper or add to local photos. */
    suspend fun saverPhoto(lastDate: String): Triple<ByteArray, String, String> = withContext(Dispatchers.IO) {
        val coroutine = kotlinx.coroutines.currentCoroutineContext()
        fetchNext("", lastDate, preferUhd = false, checkActive = { coroutine.ensureActive() })
    }

    private fun get(address: String, maxBytes: Int, checkActive: () -> Unit = {}): ByteArray {
        checkActive()
        val uri = URI(address)
        require(uri.scheme == "https" && uri.host in setOf("ygn-bing-wallpaper.pages.dev", "www.bing.com", "cdn.bimg.cc"))
        val c = CompatibleHttp.open(address)
        try {
            c.connectTimeout = 8000; c.readTimeout = 15000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "HaloTV/2.1 (https://github.com/Null9797/halo-tv)")
            check(c.responseCode == 200)
            require((c.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L) <= maxBytes)
            return c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val block = ByteArray(16384)
                while (true) { checkActive(); val n = input.read(block); if (n < 0) break; require(out.size() + n <= maxBytes); out.write(block, 0, n) }
                out.toByteArray()
            }
        } finally { c.disconnect() }
    }
    private fun fetchNext(category: String, last: String, preferUhd: Boolean = true, checkActive: () -> Unit = {}): Triple<ByteArray, String, String> {
        val index = JSONArray(String(get(BASE + "data/index.json", 4_000_000, checkActive), Charsets.UTF_8))
        val candidates = (0 until index.length()).map { index.getJSONObject(it) }.filter {
            it.optString("date").matches(Regex("[0-9]{8}")) && (category.isEmpty() || it.optString("category") == category)
        }
        require(candidates.isNotEmpty())
        // Old Bing images can expire. Try other random entries before reporting a network error.
        val pool = candidates.filter { it.optString("date") != last }.ifEmpty { candidates }.shuffled().take(if (preferUhd) 8 else 3)
        val years = mutableMapOf<String, JSONArray>()
        var failure: Exception? = null
        for (choice in pool) {
            try {
                val date = choice.getString("date")
                val year = years.getOrPut(date.take(4)) {
                    JSONArray(String(get(BASE + "data/${date.take(4)}.json", 2_000_000, checkActive), Charsets.UTF_8))
                }
                val entry = (0 until year.length()).asSequence().map { year.getJSONObject(it) }.first { it.optString("date") == date }
                val url = entry.getString("url")
                val bytes = if (preferUhd && entry.optBoolean("uhd")) runCatching { get(url.replace("_1920x1080.jpg", "_UHD.jpg"), 32_000_000, checkActive) }.getOrElse { get(url, 16_000_000, checkActive) } else get(url, 16_000_000, checkActive)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                return Triple(bytes, choice.optString("copyright"), date)
            } catch (e: Exception) {
                if (e is CancellationException || e is InterruptedException) throw e
                failure = e
                Log.w("OnlineRotation", "skipped unavailable archive entry ${choice.optString("date")}", e)
            }
        }
        throw failure ?: IllegalStateException("No available images")
    }

}

enum class BingRotationStatus { Idle, Loading, Started, Failed }

/** Uniform random index, avoiding immediate repeats when alternatives exist. */
internal fun randomOnlineIndex(current: Int, count: Int, random: kotlin.random.Random = kotlin.random.Random.Default): Int {
    require(count > 0)
    if (count == 1) return 0
    if (current !in 0 until count) return random.nextInt(count)
    val candidate = random.nextInt(count - 1)
    return if (candidate >= current) candidate + 1 else candidate
}
