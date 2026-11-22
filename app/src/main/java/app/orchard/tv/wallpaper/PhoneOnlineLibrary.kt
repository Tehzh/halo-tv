package app.orchard.tv.wallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Bounded phone-library requests; the TV never proxies arbitrary caller-supplied URLs. */
internal object PhoneOnlineLibrary {
    private const val BASE = "https://ygn-bing-wallpaper.pages.dev/"
    private const val INDEX_LIMIT = 4_000_000
    private const val INDEX_AGE = 6 * 60 * 60_000L
    private val dates = Regex("[0-9]{8}")
    private val thumbnails = Regex("thumbnails/[0-9]{4}/[0-9]{2}/[0-9]{8}\\.webp")

    /** Ignore unusable rows before counting/pages/Compose keys; never cache an empty catalog. */
    internal fun parseIndex(text: String): JSONArray {
        val raw = JSONArray(text)
        val seen = HashSet<String>()
        val valid = JSONArray()
        for (i in 0 until raw.length()) {
            val row = raw.optJSONObject(i) ?: continue
            val date = row.optString("date")
            if (date.matches(dates) && row.optString("thumbnail").matches(thumbnails) && seen.add(date)) valid.put(row)
        }
        require(valid.length() > 0) { "No usable wallpaper catalog entries" }
        return valid
    }
    private fun bytes(address: String, limit: Int): ByteArray {
        val uri = URI(address)
        require(uri.scheme == "https" && uri.host in setOf("ygn-bing-wallpaper.pages.dev", "www.bing.com", "cdn.bimg.cc"))
        val c = app.orchard.tv.system.CompatibleHttp.open(address)
        try {
            c.connectTimeout = 8_000; c.readTimeout = 15_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "HaloTV/3.0 (https://github.com/Null9797/halo-tv)")
            check(c.responseCode == 200)
            require((c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L) <= limit)
            return c.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buf = ByteArray(16_384)
                while (true) { val n = input.read(buf); if (n < 0) break; require(out.size() + n <= limit); out.write(buf, 0, n) }
                out.toByteArray()
            }
        } finally { c.disconnect() }
    }
    @Synchronized internal fun index(context: Context, fetch: () -> ByteArray = { bytes(BASE + "data/index.json", INDEX_LIMIT) }): JSONArray {
        app.orchard.tv.system.CompatibleHttp.init(context)
        val file = File(context.cacheDir, "phone-bing-index.json")
        val age = System.currentTimeMillis() - file.lastModified()
        if (file.isFile && file.length() in 1L..INDEX_LIMIT.toLong() && age in 0L until INDEX_AGE)
            runCatching { parseIndex(file.readText()) }.getOrNull()?.let { return it }
        val data = fetch()
        require(data.size <= INDEX_LIMIT)
        val list = parseIndex(String(data, Charsets.UTF_8))
        // A failed/partial cache write must neither hide valid network results nor poison Retry.
        val part = File(context.cacheDir, "phone-bing-index.json.part")
        runCatching {
            part.writeText(list.toString())
            check(part.renameTo(file))
        }.onFailure { android.util.Log.w("BingLibrary", "catalog cache unavailable; using downloaded catalog", it) }
        runCatching { part.delete() }
        return list
    }
    fun page(context: Context, page: Int, category: String, pageSize: Int = 24): JSONObject {
        val all = index(context); val categories = linkedSetOf<String>(); val matches = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val row = all.getJSONObject(i)
            categories.add(row.optString("category"))
            if (category.isEmpty() || row.optString("category") == category) matches.add(row)
        }
        return JSONObject().put("categories", JSONArray(categories.toList())).put("total", matches.size)
            .put("items", JSONArray().apply {
                matches.drop(page.coerceIn(0, 10000) * pageSize).take(pageSize).forEach { row ->
                    val thumb = row.optString("thumbnail")
                    if (thumb.matches(thumbnails))
                        put(JSONObject().put("date", row.getString("date")).put("title", row.optString("title"))
                            .put("credit", row.optString("copyright")).put("thumbnail", BASE + thumb))
                }
            })
    }
    private val years = object : java.util.LinkedHashMap<String, JSONArray>(2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONArray>?) = size > 2
    }
    @Synchronized private fun year(year: String): JSONArray = years[year] ?: JSONArray(
        String(bytes(BASE + "data/$year.json", 2_000_000), Charsets.UTF_8)
    ).also { years[year] = it }
    fun original(date: String): JSONObject {
        require(dates.matches(date))
        val all = year(date.take(4))
        val row = (0 until all.length()).map { all.getJSONObject(it) }.first { it.optString("date") == date }
        val url = row.getString("url"); val uri = URI(url)
        require(uri.scheme == "https" && uri.host in setOf("www.bing.com", "cdn.bimg.cc"))
        return JSONObject().put("url", url).put("credit", row.optString("copyright")).put("date", date)
    }
    fun legacyThumbnail(date: String): ByteArray {
        val url = original(date).getString("url").replace("_UHD.jpg", "_480x270.jpg")
        return bytes(url, 4_000_000)
    }
    fun picture(date: String): Pair<ByteArray, String> {
        val row = original(date)
        return bytes(row.getString("url"), 16_000_000) to row.getString("credit")
    }
}
