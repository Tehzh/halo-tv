package app.orchard.tv.lite

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28])
class MstarInputNavigationTest {
    @Test fun tclMs901PagesWorkWithoutGenericAndroidSettingsFilters() {
        val pkg = "com.tcl.mstar.settings"
        val settings = activity(pkg, "$pkg.TclSettings")
        val network = activity(pkg, "$pkg.NetworkSettingsActivity")
        assertTrue(SystemIntents.openSettings(context))
        assertEquals(settings, starts.last().component)
        assertTrue(SystemIntents.openNetwork(context))
        assertEquals(network, starts.last().component)
    }

    @Test fun privateTclNetworkPageIsNotOfferedAsAUsableRoute() {
        val pkg = "com.tcl.mstar.settings"
        activity(pkg, "$pkg.TclSettings")
        activity(pkg, "$pkg.NetworkSettingsActivity", exported = false)
        assertTrue(TclMs901Pages.settings(context).isEmpty())
        assertTrue(TclMs901Pages.network(context).isEmpty())
    }
    @Test fun documentedInputListTakesPriorityOverInformationBanner() {
        val target = activity(SETTINGS, "$SETTINGS.hotkey.InputSourceListViewActivity")
        activity(PLAYER, "$PLAYER.channel.SourceInfoActivity", SOURCE)
        assertTrue(SystemIntents.openMstarInputPicker(context))
        assertEquals(target, starts.single().component)
    }

    @Test fun rejectedInputListStillFallsBackToExistingSharpRoute() {
        rejected += activity(SETTINGS, "$SETTINGS.hotkey.InputSourceListViewActivity")
        val target = activity(PLAYER, "$PLAYER.channel.SourceInfoActivity", SOURCE)
        assertTrue(SystemIntents.openMstarInputPicker(context))
        assertEquals(target, starts.last().component)
    }
    private val app = RuntimeEnvironment.getApplication()
    private val pm = shadowOf(app.packageManager)
    private val starts = mutableListOf<Intent>()
    private val rejected = mutableSetOf<ComponentName>()
    private val context = object : ContextWrapper(app) {
        override fun startActivity(intent: Intent) {
            // Missing DEFAULT filters must be explicitly targeted, as on the affected ROMs.
            val component = intent.component ?: throw ActivityNotFoundException("No default activity")
            starts.add(Intent(intent))
            if (component in rejected) throw SecurityException("Firmware denied start")
        }
    }
    private fun activity(pkg: String, name: String, action: String? = null, default: Boolean = false,
                         system: Boolean = true, exported: Boolean = true, enabled: Boolean = true,
                         permission: String? = null): ComponentName {
        val ai = ApplicationInfo().apply {
            packageName = pkg; this.enabled = true
            flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
        }
        val info = ActivityInfo().apply {
            packageName = pkg; this.name = name; applicationInfo = ai
            this.exported = exported; this.enabled = enabled; this.permission = permission
        }
        val old = runCatching { app.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }.getOrNull().orEmpty()
        pm.installPackage(PackageInfo().apply {
            packageName = pkg; applicationInfo = ai
        })
        (old.toList() + info).forEach { pm.addOrUpdateActivity(it) }
        val component = ComponentName(pkg, name)
        if (action != null) pm.addIntentFilterForActivity(component, IntentFilter(action).apply {
            if (default) addCategory(Intent.CATEGORY_DEFAULT)
        })
        return component
    }

    @Test fun mstarActionWithoutDefaultIsExplicitlyStarted() {
        val target = activity(PLAYER, "$PLAYER.channel.RomSourcePickerActivity", SOURCE)
        assertTrue(SystemIntents.openInputPicker(context))
        assertEquals(target, starts.single().component)
        assertEquals(SOURCE, starts.single().action)
        assertTrue(starts.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun documentedSourceInfoComponentWorksWithoutAnyFilter() {
        val target = activity(PLAYER, "$PLAYER.channel.SourceInfoActivity")
        assertTrue(SystemIntents.openMstarInputPicker(context))
        assertEquals(target, starts.single().component)
    }

    @Test fun inaccessibleFoxconnPanelFallsBackToPlayer() {
        activity(SETTINGS, FOXCONN, exported = false)
        val good = activity(PLAYER, "$PLAYER.channel.SourceInfoActivity", SOURCE)
        assertTrue(SystemIntents.openMstarInputPicker(context))
        assertEquals(good, starts.single().component)
    }

    @Test fun rejectedFoxconnPanelDoesNotEndTheSearch() {
        rejected += activity(SETTINGS, FOXCONN)
        val good = activity(PLAYER, "$PLAYER.channel.SourceInfoActivity", SOURCE)
        assertTrue(SystemIntents.openMstarInputPicker(context))
        assertEquals(good, starts.last().component)
    }

    @Test fun renamedExportedPickerIsFoundOnlyInKnownSystemFirmware() {
        val good = activity(SETTINGS, "com.foxconn.etvg.source.NewInputSourceActivity")
        assertTrue(SystemIntents.hasMstarInputFirmware(context))
        assertTrue(SystemIntents.openMstarInputPicker(context))
        assertEquals(good, starts.single().component)
    }

    @Test fun privateDisabledProtectedFactoryAndResetPagesAreExcluded() {
        activity(SETTINGS, "com.foxconn.PrivateInputSourceActivity", exported = false)
        activity(SETTINGS, "com.foxconn.DisabledSourcePickerActivity", enabled = false)
        activity(SETTINGS, "com.foxconn.ProtectedSourceInfoActivity", permission = "vendor.signature_only")
        activity(SETTINGS, "com.foxconn.FactoryInputSourceActivity")
        activity(SETTINGS, "com.foxconn.ResetInputSourceActivity")
        assertFalse(SystemIntents.openMstarInputPicker(context))
        assertTrue(starts.isEmpty())
    }

    @Test fun similarlyNamedUserAppsAndOtherSystemPackagesAreNotScanned() {
        activity(SETTINGS, FOXCONN, system = false)
        activity("other.settings", "other.settings.SourcePickerActivity")
        assertFalse(SystemIntents.hasMstarInputFirmware(context))
        assertFalse(SystemIntents.openMstarInputPicker(context))
        assertTrue(starts.isEmpty())
    }

    @Test fun failedPickerKeepsSettingsFallbackAndItsParentalGateAvailable() {
        activity(SETTINGS, FOXCONN, exported = false)
        val systemSettings = activity("vendor.settings", "vendor.settings.Main", Settings.ACTION_SETTINGS)
        Ui.init(app); LiteSettings.init(app); Kids.init(app)
        var closed = false
        var unavailable = false
        var authorized: (() -> Unit)? = null
        val center = ControlCenter(context, { closed = true }, CcPage.Inputs,
            open = {}, locked = { authorized = it }, personalize = {}, wallpaper = {}, screensaver = {},
            showSupport = {}, showNotice = {}, showAnnouncements = {}, colorChanged = {}, goTo = {},
            bootLabel = null, clearBoot = {}, noInputs = { unavailable = true })
        val native = rows(center).single { it.text == context.getString(R.string.lite_inputs_native) }
        native.click()
        assertTrue(unavailable)
        assertFalse(closed)
        val settings = rows(center).single { it.text == context.getString(R.string.lite_inputs_settings) }
        settings.click()
        assertTrue("Settings must wait for parental approval", starts.isEmpty())
        assertNotNull(authorized)
        authorized!!.invoke()
        assertEquals(systemSettings, starts.single().component)
        assertTrue(closed)
    }

    @Test fun successfulNativePickerClosesHaloControlCenter() {
        val good = activity(PLAYER, "$PLAYER.channel.SourceInfoActivity", SOURCE)
        Ui.init(app); LiteSettings.init(app); Kids.init(app)
        var closed = false
        val center = ControlCenter(context, { closed = true }, CcPage.Inputs,
            open = {}, locked = { it() }, personalize = {}, wallpaper = {}, screensaver = {},
            showSupport = {}, showNotice = {}, showAnnouncements = {}, colorChanged = {}, goTo = {},
            bootLabel = null, clearBoot = {}, noInputs = { fail("Picker exists") })
        rows(center).single { it.text == context.getString(R.string.lite_inputs_native) }.click()
        assertEquals(good, starts.single().component)
        assertTrue(closed)
    }

    private fun rows(view: View): List<MenuRow> = when (view) {
        is MenuRow -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { rows(view.getChildAt(it)) }
        else -> emptyList()
    }

    private companion object {
        const val PLAYER = "com.mstar.tv.tvplayer.ui"
        const val SETTINGS = "com.mstar.tvsetting"
        const val SOURCE = "$PLAYER.intent.action.SOURCE_INFO"
        const val FOXCONN = "com.foxconn.etvg.inputsource.FxcInputSourceActitvity"
    }
}
