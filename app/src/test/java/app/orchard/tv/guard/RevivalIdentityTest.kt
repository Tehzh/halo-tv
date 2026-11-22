package app.orchard.tv.guard

import android.accounts.AccountManager
import android.content.ComponentName
import android.content.ContentResolver
import android.os.Looper
import android.os.UserManager
import app.orchard.tv.R
import java.io.FileDescriptor
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class RevivalIdentityTest {
    private val app = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
    }

    @Test fun independentRevivalComponentsAndAccountsUseInstalledIdentity() {
        // Kotlin's namespace is intentionally retained; Android component packages and account
        // ownership must nevertheless belong to applicationId, not to the original app.
        for (type in listOf(HomeGuardService::class.java, RevivalListener::class.java, RevivalSyncService::class.java, RevivalAccountService::class.java)) {
            val component = ComponentName(app, type)
            val info = app.packageManager.getServiceInfo(component, 0)
            assertEquals(app.packageName, info.packageName)
            assertEquals(type.name, info.name)
        }
        assertEquals(app.packageName, app.getString(R.string.halo_account_type))
        ContentResolver.setMasterSyncAutomatically(false)
        RevivalSync.ensure(app)
        val accounts = AccountManager.get(app).getAccountsByType(app.packageName)
        assertEquals(1, accounts.size)
        assertEquals(app.packageName, accounts.single().type)
        assertFalse("A local revival adapter must not turn global sync on", ContentResolver.getMasterSyncAutomatically())
    }







    private fun snapshot(listener: RevivalListener): String = StringWriter().also {
        RevivalListener::class.java.getDeclaredMethod("dump", FileDescriptor::class.java, PrintWriter::class.java, Array<String>::class.java)
            .apply { isAccessible = true }.invoke(listener, null, PrintWriter(it), null)
    }.toString()
}
