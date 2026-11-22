package app.orchard.tv.lite

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LiteUpdateReturnTest {
    private val app = RuntimeEnvironment.getApplication()
    @Test fun pendingInstallIsNotTreatedAsReplacement() {
        LiteUpdateReturn.noteInstall(app, BuildConfig.VERSION_CODE + 1)
        assertFalse(LiteUpdateReturn.keepHome(app))
    }
    @Test fun replacedApkMarksBootTargetHandledWithoutChangingIt() {
        app.getSharedPreferences("halo_update_return", Context.MODE_PRIVATE).edit().clear()
            .putInt("from", BuildConfig.VERSION_CODE - 1).putInt("target", BuildConfig.VERSION_CODE)
            .putLong("at", android.os.SystemClock.elapsedRealtime()).commit()
        assertTrue(LiteUpdateReturn.claim(app))
        assertFalse(LiteUpdateReturn.claim(app))
        assertTrue(LiteUpdateReturn.keepHome(app))
        assertTrue(app.getSharedPreferences("boot_open", Context.MODE_PRIVATE).contains("elapsed"))
    }
    @Test fun cancellationClearsReturnRequest() {
        LiteUpdateReturn.noteInstall(app, BuildConfig.VERSION_CODE + 1)
        LiteUpdateReturn.cancel(app)
        assertFalse(LiteUpdateReturn.claim(app))
    }
}
