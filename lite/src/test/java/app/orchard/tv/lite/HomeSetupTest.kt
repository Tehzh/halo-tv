package app.orchard.tv.lite

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28])
class HomeSetupTest {
    private val app = RuntimeEnvironment.getApplication()
    private var approval: (() -> Unit)? = null
    private val launches = mutableListOf<Intent>()
    private fun panel(): ControlCenter {
        Ui.init(app); LiteSettings.init(app); Kids.init(app)
        return ControlCenter(app, {}, CcPage.Setup, open = { launches.add(it) }, locked = { approval = it },
            personalize = {}, wallpaper = {}, screensaver = {}, showSupport = {}, showNotice = {},
            showAnnouncements = {}, colorChanged = {}, goTo = {}, bootLabel = null, clearBoot = {}, noInputs = {})
    }
    private fun rows(view: View): List<MenuRow> = when (view) {
        is MenuRow -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { rows(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun homePermissionStaysBehindParentGateAndDoesNotEditGuardAuthorization() {
        val component = ComponentName("vendor.settings", "vendor.settings.Home")
        shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
            packageName = component.packageName; name = component.className; exported = true
            applicationInfo = ApplicationInfo().apply { packageName = component.packageName }
        })
        shadowOf(app.packageManager).addIntentFilterForActivity(component,
            IntentFilter(Settings.ACTION_HOME_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        val before = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val controls = rows(panel())
        assertFalse(controls.any { it.text == app.getString(R.string.halo_setup_activate) })
        controls.single { it.text == app.getString(R.string.cc_setup_home) }.click()
        assertNotNull(approval)
        assertTrue(launches.isEmpty())
        approval!!.invoke()
        assertEquals(Settings.ACTION_HOME_SETTINGS, launches.single().action)
        assertEquals(before, Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
    }

    @Test fun missingVendorPermissionPageReportsUnavailableInsteadOfSilentlyFailing() {
        rows(panel()).single { it.text == app.getString(R.string.halo_setup_app_details) }.click()
        approval!!.invoke()
        assertTrue(launches.isEmpty())
        assertEquals(app.getString(R.string.halo_setup_no_entry), ShadowToast.getTextOfLatestToast())
    }

    @Test fun guardButtonOpensSystemConsentBehindParentGate() {
        val component = ComponentName("vendor.settings", "vendor.settings.Accessibility")
        shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
            packageName = component.packageName; name = component.className; exported = true
            applicationInfo = ApplicationInfo().apply { packageName = component.packageName }
        })
        shadowOf(app.packageManager).addIntentFilterForActivity(component,
            IntentFilter(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        rows(panel()).single { it.text == app.getString(R.string.lite_guard_open) }.click()
        assertTrue(launches.isEmpty())
        approval!!.invoke()
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, launches.single().action)
        assertFalse(LiteHomeGuard.authorized(app))
    }

}
