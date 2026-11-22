package app.orchard.tv

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.UserManager
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.guard.HomeGuardService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InputHomeVisitTest {
    @Test fun inputHomeIsAllowedButUnrelatedOrOwnPackageIsNot() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = ActivityInfo().apply {
            packageName = "com.jmgo.tv"; name = "com.jmgo.tv.MainActivity"
            applicationInfo = ApplicationInfo().apply { packageName = "com.jmgo.tv" }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(home, ResolveInfo().apply { activityInfo = info })
        HomeGuardService.onAerialResumed(activity)
        activity.allowInputThroughHome(Intent().setComponent(ComponentName("other.tv", "other.tv.Input")))
        assertEquals(0L, GuardSetup.stockVisitLeftMs(activity))
        activity.allowInputThroughHome(Intent().setComponent(ComponentName(app.packageName, MainActivity::class.java.name)))
        assertEquals(0L, GuardSetup.stockVisitLeftMs(activity))
        activity.allowInputThroughHome(Intent().setComponent(ComponentName("com.jmgo.tv", "com.jmgo.tv.MainActivity")))
        assertTrue(GuardSetup.stockVisitLeftMs(activity) > 0L)
        HomeGuardService.onAerialResumed(activity)
    }
}
