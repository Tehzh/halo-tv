package app.orchard.tv.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WeatherDiagnosticsTest {
    @Test fun reportIncludesStateWithoutPlaceOrCoordinates() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("weather", 0).edit()
            .putString("place", "PrivatePlaceName").putFloat("lat", 12.345f)
            .putFloat("lon", 67.89f).commit()
        Weather.init(context)
        val report = Weather.diagnostics()
        assertTrue(report.contains("located=true"))
        assertTrue(report.contains("result=none"))
        assertFalse(report.contains("PrivatePlaceName"))
        assertFalse(report.contains("12.345"))
        assertFalse(report.contains("67.89"))
    }
}
