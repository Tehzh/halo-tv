package app.orchard.tv.compat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import app.orchard.tv.BuildConfig
import app.orchard.tv.MainActivity
import app.orchard.tv.guard.BootReceiver
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.guard.HomeGuardService
import app.orchard.tv.guard.RevivalListener
import app.orchard.tv.guard.RevivalSyncService
import app.orchard.tv.system.Brightness
import app.orchard.tv.system.CompatibleHttp
import app.orchard.tv.system.MitvPanel
import app.orchard.tv.ui.home.*
import app.orchard.tv.ui.wallpaper.decodeLibraryThumbnail
import app.orchard.tv.wallpaper.ScreensaverMode
import app.orchard.tv.weather.Weather
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.ByteArrayOutputStream
import javax.net.ssl.HttpsURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompatibilityRegressionTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before fun cleanVendorState() {
        ReflectionHelpers.setStaticField(MitvPanel::class.java, "verified", null)
        ReflectionHelpers.setStaticField(Brightness::class.java, "writeFailed", false)
        com.xgimi.gmpf.api.DisplayManager.getInstance().unavailable = true
    }
    @After fun restoreVendorState() {
        ReflectionHelpers.setStaticField(MitvPanel::class.java, "verified", null)
        ReflectionHelpers.setStaticField(Brightness::class.java, "writeFailed", false)
        com.xgimi.gmpf.api.DisplayManager.getInstance().unavailable = false
    }

    @Test fun homeCategoryCombinationsResolveOnlyInStandard() {
        val standard = BuildConfig.FLAVOR == "standard"
        for (extra in listOf(emptyList(), listOf(Intent.CATEGORY_LAUNCHER), listOf("android.intent.category.COMMON_HOME"), listOf(Intent.CATEGORY_LAUNCHER, "android.intent.category.COMMON_HOME"))) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
            extra.forEach(intent::addCategory)
            val ours = context.packageManager.queryIntentActivities(intent, 0).any {
                it.activityInfo.name == MainActivity::class.java.name
            }
            assertEquals(extra.toString(), standard, ours)
        }
    }

    @Test fun standardCarriesTclUpdateVerifierMarker() {
        if (BuildConfig.FLAVOR == "standard") {
            val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            assertEquals("com.tcl.ui", info.metaData.getString("com.tcl.app.type"))
        }
    }

    @Test fun replacingPackageRepairsWithoutOpeningHomeOverAnApp() {
        ReflectionHelpers.callInstanceMethod<Unit>(BootReceiver(), "onBoot",
            ClassParameter.from(Context::class.java, context),
            ClassParameter.from(String::class.java, Intent.ACTION_MY_PACKAGE_REPLACED),
            ClassParameter.from(Long::class.javaPrimitiveType, SystemClock.elapsedRealtime()))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test fun guardRecoveryRestoresDisabledComponentsAndPreservesOtherAccessibilityServices() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        val components = listOf(HomeGuardService::class.java, RevivalListener::class.java, RevivalSyncService::class.java)
        components.forEach { context.packageManager.setComponentEnabledSetting(ComponentName(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
        val other = "example.reader/example.reader.Accessibility"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, other)
        GuardSetup.ensureEnabled(context)
        components.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(ComponentName(context, it))) }
        val result = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).split(':')
        assertTrue(result.contains(other))
        assertTrue(result.contains(ComponentName(context, HomeGuardService::class.java).flattenToString()))
    }

    @Test fun onlineSourceCannotCarryTheLocalVideoOrPhotoPlayer() {
        assertFalse(carryMatchesMode(SaverCarry.Video(HomeVideo()), ScreensaverMode.Aerial))
        assertFalse(carryMatchesMode(SaverCarry.Video(HomeVideo()), ScreensaverMode.Bing))
        assertTrue(carryMatchesMode(SaverCarry.Video(HomeVideo()), ScreensaverMode.Videos))
        assertFalse(carryMatchesMode(SaverCarry.Film(LiveFilm()), ScreensaverMode.Photos))
        assertTrue(carryMatchesMode(SaverCarry.Film(LiveFilm()), ScreensaverMode.Aerial))
        assertFalse(carryMatchesMode(null, ScreensaverMode.FlipClock))
    }

    @Test fun onlineSurfaceFillsHdAndUhdWithoutRescalingTheUi() {
        assertEquals(1f to 1f, textureCoverScale(1920, 1080, 3840, 2160, 1f))
        assertEquals(1f to 1f, textureCoverScale(3840, 2160, 1920, 1080, 1f))
        val narrow = textureCoverScale(1440, 1080, 1920, 1080, 1f)
        assertEquals(4f / 3f, narrow.first, 0.001f); assertEquals(1f, narrow.second)
        val upright = textureCoverScale(1920, 1080, 1080, 1920, 1f)
        assertEquals(1f, upright.first); assertTrue(upright.second > 3f)
        assertEquals(1f to 1f, textureCoverScale(0, 1080, 1920, 1080, 1f))
        assertEquals(1f to 1f, textureCoverScale(1920, 1080, 1920, 1080, Float.NaN))
    }

    @Test fun thumbnailsAreBoundedEvenWhenProviderReturnsAnOriginal() {
        val bitmap = Bitmap.createBitmap(2560, 1440, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val thumbnail = decodeLibraryThumbnail(bytes)!!
        assertTrue(thumbnail.width <= 640); assertTrue(thumbnail.height <= 400)
        thumbnail.recycle()
        assertNull(decodeLibraryThumbnail(byteArrayOf(1, 2, 3)))
    }

    @Test fun temporaryWeatherFailureDoesNotEraseAReadingFromEarlierToday() {
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("fetched_at", System.currentTimeMillis() - 4 * 60 * 60_000L)
            .putString("kind", "Clear").putInt("celsius", 22).putString("place", "Test City").commit()
        Weather.init(context)
        assertEquals(22, Weather.now?.celsius)
        assertEquals("Test City", Weather.place)
    }

    @Test fun failedNativeBrightnessWriteFallsBackWithoutReportingASuccess() {
        ReflectionHelpers.setStaticField(MitvPanel::class.java, "verified", true)
        ReflectionHelpers.setStaticField(Brightness::class.java, "writeFailed", false)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 100)
        // No TvService binder in this environment: a previously available service has gone away.
        val caller = object : ContextWrapper(context) {
            private val loader = object : ClassLoader(context.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name == "com.changhong.tvos.common.TVManager") throw ClassNotFoundException(name)
                    return super.loadClass(name, resolve)
                }
            }
            override fun getClassLoader(): ClassLoader = loader
        }
        assertNull(Brightness.adjust(caller, 1))
        assertNull(Brightness.level(caller))
        assertEquals(100, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
    }

    @Test @Config(sdk = [23]) fun legacyTlsAddsRootsWithoutDisablingHostnameValidation() {
        CompatibleHttp.init(context)
        val defaultFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val connection = CompatibleHttp.open("https://sylvan.apple.com/example.png") as HttpsURLConnection
        assertNotSame(defaultFactory, connection.sslSocketFactory)
        assertSame(defaultFactory, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertSame(HttpsURLConnection.getDefaultHostnameVerifier(), connection.hostnameVerifier)
        connection.disconnect()
    }
}
