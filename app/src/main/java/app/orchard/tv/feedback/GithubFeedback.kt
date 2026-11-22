package app.orchard.tv.feedback

import android.os.Build
import org.json.JSONObject

internal object GithubFeedback {
    const val URL = "https://github.com/Null9797/halo-tv/issues/new?template=bug_report.yml"

    const val PHONE_PATH = "#diagnostics"

    /** Compact public fields only. Full logs remain on the phone until the user shares them. */
    fun summary(version: String, edition: String, screen: String, health: String): JSONObject = JSONObject()
        .put("model", sanitize(Build.MODEL.orEmpty()).filterNot { it.isISOControl() }.take(80))
        .put("android", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        .put("version", version)
        .put("edition", edition)
        .put("evidence", "Screen: $screen\n$health")

    fun sanitize(value: String): String = value.take(100_000)
        .replace(Regex("(?i)https?://[^\\s<>\"']+"), "<url>")
        .replace(Regex("(?i)(?:/storage/|/sdcard/|/data/user/)[^\\s]+"), "<private-path>")
        .replace(Regex("(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w.])"), "<ip>")
        .replace(Regex("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b"), "<mac>")
        .replace(Regex("(?i)(?<![a-z0-9])(?:[0-9a-f]{0,4}:){2,}[0-9a-f]{0,4}(?:%[a-z0-9]+)?"), "<ip>")
        .replace(Regex("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"), "<email>")
        .replace(Regex("(?i)(password|passwd|token|authorization|pin|ssid)\\s*[=:]\\s*[^\\s,;]+"), "$1=<redacted>")
        .take(80_000)
}
