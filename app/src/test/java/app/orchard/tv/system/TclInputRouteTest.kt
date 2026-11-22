package app.orchard.tv.system

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class TclInputRouteTest {
    private val app = RuntimeEnvironment.getApplication()
    private val player = ComponentName("com.tcl.tv", "com.tcl.tv.TVActivity")
    @Test fun androidFourteenHardwareIdsAreUsedWithoutLabelOffsets() {
        for (hardware in 15..18) {
            val intent = SystemProbe.tclRoute(player, SourceKind.Hdmi, hardware)!!
            assertEquals(hardware, intent.getIntExtra("targetSourceId", -1))
            assertEquals(player, intent.component)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        }
    }
    @Test fun unknownHardwareDoesNotGuessPortFromDisplayName() {
        assertNull(SystemProbe.tclRoute(player, SourceKind.Hdmi, null))
    }
    @Test fun passthroughRetainsCompleteInputIdentity() {
        val id = "com.tcl.tvinput/.services.HdmiInputService/HW18"
        val intent = SystemProbe.passthroughIntent(id)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(id, intent.data?.lastPathSegment)
        assertEquals("android.media.tv", intent.data?.authority)
    }
    private fun handler(intent: Intent, system: Boolean, permission: String? = null, television: Boolean = true, exported: Boolean = true) {
        val info = ResolveInfo().apply {
            isDefault = true
            filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("content")
                if (television) addDataAuthority("android.media.tv", null)
            }
            activityInfo = ActivityInfo().apply {
                packageName = "vendor.input"; name = "vendor.input.Main"
                this.exported = exported; enabled = true; this.permission = permission
                applicationInfo = ApplicationInfo().apply {
                    packageName = "vendor.input"; enabled = true
                    flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
                }
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(intent, info)
    }
    @Test fun arbitraryContentFileManagerIsNotAcceptedAsTelevisionInputHandler() {
        val intent = SystemProbe.passthroughIntent("vendor/.Input/HW15")
        handler(intent, false)
        assertNull(SystemProbe.systemTvRoute(app, intent))
    }
    @Test fun accessibleSystemHandlerIsPinnedToItsActualComponent() {
        val intent = SystemProbe.passthroughIntent("vendor/.Input/HW15")
        handler(intent, true)
        val routed = SystemProbe.systemTvRoute(app, intent)!!
        assertEquals(intent.data, routed.data)
        assertEquals(ComponentName("vendor.input", "vendor.input.Main"), routed.component)
    }
    @Test fun inaccessibleSystemHandlerIsNotChosen() {
        val intent = SystemProbe.passthroughIntent("vendor/.Input/HW15")
        handler(intent, true, "vendor.signature.INPUT")
        assertNull(SystemProbe.systemTvRoute(app, intent))
    }
    @Test fun systemFileManagerWithOnlyBroadContentFilterIsNotTelevisionRouting() {
        val intent = SystemProbe.passthroughIntent("vendor/.Input/HW15")
        handler(intent, true, television = false)
        assertNull(SystemProbe.systemTvRoute(app, intent))
    }
    @Test fun unexportedTelevisionHandlerCannotBeSelected() {
        val intent = SystemProbe.passthroughIntent("vendor/.Input/HW15")
        handler(intent, true, exported = false)
        assertNull(SystemProbe.systemTvRoute(app, intent))
    }
}
