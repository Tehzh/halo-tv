package app.orchard.tv.lite

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InstallerIntentTest {
    @Test fun bothInstallerRoutesCarryReadableApkWithoutWriteAccess() {
        val uri = Uri.parse("content://app.halotv.launcher.files/updates/aerial.apk")
        val intents = LiteUpdate.installerIntents(uri)
        assertEquals(listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE), intents.map { it.action })
        for (intent in intents) {
            assertEquals(uri, intent.data)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}
