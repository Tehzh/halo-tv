package app.orchard.tv.lite

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppLabelTest {
    @Test fun labelIsReadAgainRatherThanReusedWithCachedArtwork() {
        val info = ActivityInfo().apply { nonLocalizedLabel = "English" }
        val pm = RuntimeEnvironment.getApplication().packageManager
        assertEquals("English", currentAppLabel(pm, info, "app.test"))
        info.nonLocalizedLabel = "中文"
        assertEquals("中文", currentAppLabel(pm, info, "app.test"))
    }
}
