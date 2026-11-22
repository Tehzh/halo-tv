package app.orchard.tv.system

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.net.ssl.HttpsURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class AerialThumbnailTlsTest {
    @Test fun legacyAppleTrustDoesNotChangeGlobalTrustOrHostVerification() {
        val context = RuntimeEnvironment.getApplication()
        val defaultSocket = HttpsURLConnection.getDefaultSSLSocketFactory()
        val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val connection = CompatibleHttp.openAerialThumbnail(context, "https://sylvan.apple.com/test.png")
        try {
            assertNotSame(defaultSocket, connection.sslSocketFactory)
            assertSame(defaultVerifier, connection.hostnameVerifier)
            assertFalse(connection.instanceFollowRedirects)
            assertSame(defaultSocket, HttpsURLConnection.getDefaultSSLSocketFactory())
            assertThrows(IllegalArgumentException::class.java) {
                CompatibleHttp.openAerialThumbnail(context, "https://sylvan.apple.com.example.org/test.png")
            }
            assertThrows(IllegalArgumentException::class.java) {
                CompatibleHttp.openAerialThumbnail(context, "http://sylvan.apple.com/test.png")
            }
        } finally { connection.disconnect() }
    }
}
