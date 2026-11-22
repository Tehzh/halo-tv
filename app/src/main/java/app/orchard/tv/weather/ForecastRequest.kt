package app.orchard.tv.weather

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import org.json.JSONObject

/** Shared by both launchers so old TV TLS and forecast retries follow the same rules. */
internal object ForecastRequest {
    private const val LIMIT = 64 * 1024

    fun load(lat: Float, lon: Float): JSONObject {
        val query = "latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
        return fetch("https://api.open-meteo.com/v1/forecast?$query") { url, fresh ->
            read(app.orchard.tv.system.CompatibleHttp.open(url), fresh)
        }
    }

    // Reopen a stale connection on the same secure endpoint before using the existing
    // cleartext compatibility route on firmware whose TLS handshake never completes.
    internal fun fetch(url: String, request: (String, Boolean) -> String): JSONObject {
        return try {
            JSONObject(request(url, false))
        } catch (_: SocketTimeoutException) {
            try {
                JSONObject(request(url, true))
            } catch (retry: IOException) {
                if (retry !is SocketTimeoutException && retry !is SSLException) throw retry
                JSONObject(request(url.replaceFirst("https://", "http://"), true))
            }
        } catch (_: SSLException) {
            JSONObject(request(url.replaceFirst("https://", "http://"), true))
        }
    }

    private fun read(connection: HttpURLConnection, fresh: Boolean): String {
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = if (fresh) 9_000 else 6_000
            connection.useCaches = false
            if (fresh) connection.setRequestProperty("Connection", "close")
            val status = connection.responseCode
            if (status !in 200..299) throw Status(status)
            return connection.inputStream.use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (bytes.size() + read > LIMIT) throw IOException("Forecast response exceeds size limit")
                    bytes.write(buffer, 0, read)
                }
                bytes.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }

    class Status(code: Int) : IOException("HTTP $code")
}
