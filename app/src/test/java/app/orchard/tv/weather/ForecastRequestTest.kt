package app.orchard.tv.weather

import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ForecastRequestTest {
    private val url = "https://api.open-meteo.com/v1/forecast"
    private val forecast = """{"current":{"temperature_2m":20}}"""

    @Test fun timeoutRetriesHttpsBeforeFallingBack() {
        val attempts = mutableListOf<Pair<String, Boolean>>()
        val result = ForecastRequest.fetch(url) { address, fresh ->
            attempts += address to fresh
            if (!fresh) throw SocketTimeoutException()
            forecast
        }
        assertEquals(20, result.getJSONObject("current").getInt("temperature_2m"))
        assertEquals(listOf(url to false, url to true), attempts)
    }

    @Test fun timeoutOnBothHttpsConnectionsStillTriesTheLegacyRoute() {
        val attempts = mutableListOf<Pair<String, Boolean>>()
        ForecastRequest.fetch(url) { address, fresh ->
            attempts += address to fresh
            if (address.startsWith("https:")) throw SocketTimeoutException()
            forecast
        }
        assertEquals(listOf(url to false, url to true, url.replace("https:", "http:") to true), attempts)
    }

    @Test fun preservesLegacyTlsCompatibilityButDoesNotRepeatHttpErrors() {
        val attempts = mutableListOf<String>()
        ForecastRequest.fetch(url) { address, _ ->
            attempts += address
            if (address.startsWith("https:")) throw SSLHandshakeException("legacy TLS")
            forecast
        }
        assertEquals(listOf(url, url.replace("https:", "http:")), attempts)
        attempts.clear()
        assertThrows(ForecastRequest.Status::class.java) {
            ForecastRequest.fetch(url) { address, _ ->
                attempts += address
                throw ForecastRequest.Status(429)
            }
        }
        assertEquals(listOf(url), attempts)
    }
}
