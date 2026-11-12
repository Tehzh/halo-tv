package app.orchard.tv.announcements

import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A small, versioned feed. Invalid remote data never becomes a partially interpreted notice. */
internal object AnnouncementFeed {
    fun parse(text: String): List<RemoteAnnouncement> {
        require(text.toByteArray(Charsets.UTF_8).size <= AnnouncementPolicy.MAX_BYTES)
        val root = JSONObject(text)
        require(root.getInt("schema") == 1)
        if (!root.getBoolean("enabled")) return emptyList()
        val list = root.getJSONArray("announcements")
        require(list.length() <= 20)
        val result = ArrayList<RemoteAnnouncement>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.has("enabled") && !o.getBoolean("enabled")) continue
            val id = o.getString("id").also { require(it.matches(Regex("[A-Za-z0-9._-]{1,64}"))) }
            val revision = o.getInt("revision").also { require(it in 1..1_000_000) }
            val title = o.getString("title").trim().also { require(it.length in 1..100) }
            val body = o.getString("body").trim().also { require(it.length in 1..6000) }
            val kind = o.optString("kind", "info").also { require(it == "info" || it == "important") }
            require(!o.has("variants") || o.isNull("variants") || o.optJSONArray("variants") != null)
            val variants = o.optJSONArray("variants")?.let { a ->
                (0 until a.length()).map { a.getString(it).also { v -> require(v in setOf("guard", "standard", "lite")) } }.toSet()
            } ?: emptySet()
            fun version(key: String): String? = if (o.isNull(key)) null else o.getString(key).also {
                require(it.matches(Regex("[0-9]{1,6}(\\.[0-9]{1,6}){1,3}")))
            }
            fun code(key: String): Long? = if (o.isNull(key)) null else o.getLong(key).also { require(it > 0) }
            val min = version("minVersion"); val max = version("maxVersion")
            val minCode = code("minCode"); val maxCode = code("maxCode")
            require(min == null || max == null || AnnouncementPolicy.compare(min, max) <= 0)
            require(minCode == null || maxCode == null || minCode <= maxCode)
            val from = date(o, "startsAt", 0L); val until = date(o, "expiresAt", Long.MAX_VALUE)
            require(from < until)
            val url = o.optString("detailsUrl").takeIf { it.length <= 2048 && AnnouncementPolicy.trustedDetails(it) }
            val n = RemoteAnnouncement(id, revision, title, body, kind == "important", variants, min, max, minCode, maxCode, from, until, url)
            require(result.none { it.key == n.key })
            result.add(n)
        }
        // Importance first; within one class, the author's array order is the order displayed.
        return result.sortedByDescending { it.important }
    }

    private fun date(o: JSONObject, key: String, fallback: Long): Long {
        if (o.isNull(key) || o.optString(key).isEmpty()) return fallback
        val value = o.getString(key)
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")))
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
        }
        val pos = ParsePosition(0)
        return (parser.parse(value, pos)?.time).also { require(it != null && pos.index == value.length) }!!
    }
}
