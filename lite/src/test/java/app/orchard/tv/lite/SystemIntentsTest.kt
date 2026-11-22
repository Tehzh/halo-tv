package app.orchard.tv.lite

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28])
class SystemIntentsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val pm = shadowOf(app.packageManager)
    private val starts = mutableListOf<Intent>()
    private val rejected = mutableSetOf<String>()
    private val context = object : ContextWrapper(app) {
        override fun startActivity(intent: Intent) {
            starts.add(Intent(intent))
            if (intent.component?.packageName in rejected) throw SecurityException("Firmware rejected this route")
        }
    }

    private fun activity(pkg: String, action: String, category: String? = Intent.CATEGORY_DEFAULT,
                         system: Boolean = true, enabled: Boolean = true, exported: Boolean = true,
                         permission: String? = null, name: String = "$pkg.Main"): ComponentName {
        val ai = ApplicationInfo().apply {
            packageName = pkg; this.enabled = enabled
            flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
        }
        val info = ActivityInfo().apply {
            packageName = pkg; this.name = name; applicationInfo = ai
            this.enabled = enabled; this.exported = exported; this.permission = permission
        }
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = ai })
        val component = ComponentName(pkg, name)
        pm.addOrUpdateActivity(info)
        pm.addIntentFilterForActivity(component, IntentFilter(action).apply {
            category?.let(::addCategory)
            if (action == Intent.ACTION_DELETE || action == Intent.ACTION_UNINSTALL_PACKAGE ||
                action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) addDataScheme("package")
        })
        return component
    }

    @Test fun settingsFallsBackAfterResolvedActivityRejectsLaunch() {
        activity("broken.settings", Settings.ACTION_SETTINGS)
        val good = activity("vendor.settings", Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER)
        rejected.add("broken.settings")
        assertTrue(SystemIntents.openSettings(context))
        assertEquals(good, starts.last().component)
    }

    @Test fun networkCanOpenFirmwareMainWithoutStandardActions() {
        val good = activity("com.baofengtv.settings", Intent.ACTION_MAIN, category = null)
        assertTrue(SystemIntents.openNetwork(context))
        assertEquals(good, starts.single().component)
    }

    @Test fun wifiFilterWithoutDefaultStillWorks() {
        val good = activity("vendor.network", Settings.ACTION_WIFI_SETTINGS, category = null)
        assertTrue(SystemIntents.openNetwork(context))
        assertEquals(good, starts.single().component)
    }

    @Test fun wirelessSettingsAreUsedBeforeGenericSettings() {
        val good = activity("vendor.wireless", Settings.ACTION_WIRELESS_SETTINGS)
        activity("vendor.settings", Settings.ACTION_SETTINGS)
        assertTrue(SystemIntents.openNetwork(context))
        assertEquals(good, starts.single().component)
    }

    @Test fun settingsFallbackRejectsDisabledPrivateProtectedFactoryAndThirdPartyApps() {
        activity("disabled.settings", Intent.ACTION_MAIN, enabled = false)
        activity("private.settings", Intent.ACTION_MAIN, exported = false)
        activity("protected.settings", Intent.ACTION_MAIN, permission = "vendor.permission.SIGNATURE_ONLY")
        activity("thirdparty.settings", Intent.ACTION_MAIN, system = false)
        activity("vendor.settings", Intent.ACTION_MAIN, name = "vendor.settings.FactoryActivity")
        assertFalse(SystemIntents.openSettings(context))
        assertTrue(starts.isEmpty())
    }

    @Test fun unknownPackageCanAdvertiseOfficialSettingsCategory() {
        val good = activity("vendor.control", Intent.ACTION_MAIN, "android.intent.category.LEANBACK_SETTINGS")
        assertTrue(SystemIntents.openSettings(context))
        assertEquals(good, starts.single().component)
    }

    @Test fun noRoutesReturnsFailureInsteadOfClaimingOpened() {
        assertFalse(SystemIntents.openSettings(context))
        assertFalse(SystemIntents.openNetwork(context))
        assertFalse(SystemIntents.openApplicationDetails(context, "user.app"))
        assertTrue(starts.isEmpty())
    }

    @Test fun uninstallWorksWithoutAppManagementAndKeepsPackageUri() {
        activity("user.app", Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER, system = false)
        val installer = activity("system.installer", Intent.ACTION_DELETE, category = null)
        assertTrue(SystemIntents.requestUninstall(context, "user.app"))
        assertEquals(installer, starts.single().component)
        assertEquals("package:user.app", starts.single().dataString)
        assertEquals(Intent.ACTION_DELETE, starts.single().action)
    }

    @Test fun uninstallTriesAlternativeWhenDeleteIsRejected() {
        activity("user.app", Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER, system = false)
        activity("broken.installer", Intent.ACTION_DELETE)
        val installer = activity("system.installer", Intent.ACTION_UNINSTALL_PACKAGE)
        rejected.add("broken.installer")
        assertTrue(SystemIntents.requestUninstall(context, "user.app"))
        assertEquals(installer, starts.last().component)
        assertEquals("package:user.app", starts.last().dataString)
    }

    @Test fun uninstallNeverOffersSystemAppsOrMissingPackages() {
        activity("system.app", Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER)
        activity("system.installer", Intent.ACTION_DELETE)
        assertFalse(SystemIntents.requestUninstall(context, "system.app"))
        assertFalse(SystemIntents.requestUninstall(context, "missing.app"))
        assertTrue(starts.isEmpty())
    }

    @Test fun missingInstallerReturnsFailure() {
        activity("user.app", Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER, system = false)
        assertFalse(SystemIntents.requestUninstall(context, "user.app"))
        assertTrue(starts.isEmpty())
    }

    @Test fun detailsPageUsesTheSelectedPackage() {
        val good = activity("system.details", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, category = null)
        assertTrue(SystemIntents.openApplicationDetails(context, "user.app"))
        assertEquals(good, starts.single().component)
        assertEquals("package:user.app", starts.single().dataString)
    }

    @Test fun fallbackInfoStartsOnBackAndDoesNotUninstallAutomatically() {
        Ui.init(app); LiteSettings.init(app)
        activity("user.app", Intent.ACTION_MAIN, system = false)
        activity("system.installer", Intent.ACTION_DELETE)
        var closed = false
        val sheet = AppInfoSheet(context, { closed = true }, "User app", "user.app")
        assertTrue(starts.isEmpty())
        sheet.focused!!.click()
        assertTrue(closed)
        assertTrue(starts.isEmpty())
    }
}
