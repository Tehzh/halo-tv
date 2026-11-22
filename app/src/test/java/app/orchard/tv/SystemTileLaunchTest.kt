package app.orchard.tv

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.geometry.Rect
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.SystemTile
import app.orchard.tv.apps.TileArtFactory
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Restricted
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SystemTileLaunchTest {
    @Test(timeout = 5000) fun retainedPinAfterFamilyModeExitDoesNotReenterLaunch() {
        val context = RuntimeEnvironment.getApplication()
        AerialSettings.init(context)
        Restricted.init(context)
        Restricted.chooseEnabled(false)
        AerialSettings.choosePin("1111")
        assertTrue(AerialSettings.locked)
        assertFalse(Restricted.active)
        val component = ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")
        val info = ActivityInfo().apply {
            packageName = component.packageName; name = component.className; exported = true
            applicationInfo = ApplicationInfo().apply { packageName = component.packageName; flags = ApplicationInfo.FLAG_SYSTEM }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Settings.ACTION_SETTINGS), ResolveInfo().apply { activityInfo = info },
        )
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val tile = AppEntry(component.packageName, "Settings", component, false, false, TileArtFactory.forSystem(SystemTile.Settings))
        try {
            MainActivity::class.java.getDeclaredMethod("launch", AppEntry::class.java, Rect::class.java).apply {
                isAccessible = true
            }.invoke(activity, tile, null)
            assertEquals(Settings.ACTION_SETTINGS, shadowOf(activity).nextStartedActivityForResult?.intent?.action)
            assertTrue(AerialSettings.pinMatches("1111"))
        } finally { AerialSettings.choosePin(null) }
    }
    @Test fun stockHomeTileLaunchesWithoutScaleAnimationOptions() {
        val context = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val component = ComponentName("vendor.home", "vendor.home.Home")
        val info = ActivityInfo().apply {
            packageName = component.packageName; name = component.className; exported = true
            applicationInfo = ApplicationInfo().apply { packageName = component.packageName }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            ResolveInfo().apply { activityInfo = info },
        )
        val tile = AppEntry(component.packageName, "Home", component, false, false, TileArtFactory.forSystem(SystemTile.Home))
        MainActivity::class.java.getDeclaredMethod("launch", AppEntry::class.java, Rect::class.java).apply {
            isAccessible = true
        }.invoke(activity, tile, Rect(0f, 0f, 100f, 60f))
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(component, started?.intent?.component)
        assertNull(started?.options)
    }
}
