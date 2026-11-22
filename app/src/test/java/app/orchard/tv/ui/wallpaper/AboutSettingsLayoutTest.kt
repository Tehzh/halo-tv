package app.orchard.tv.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-w960dp-h540dp-land-xhdpi")
class AboutSettingsLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    @After fun close() { compose.runOnUiThread { host?.close() } }

    @Test fun chineseAboutFitsAvailableHeightAtFullTextSize() {
        val height = mutableIntStateOf(0)
        var density = 1f
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = host!!.get()
            density = activity.resources.displayMetrics.density
            activity.setContent {
                Box(Modifier.width(684.dp)) {
                    AboutSettingsContent(Modifier.onSizeChanged { height.intValue = it.height })
                }
            }
        }
        compose.waitForIdle()
        assertTrue("about height ${height.intValue / density}dp exceeds 410dp", height.intValue > 0 && height.intValue / density <= 410f)
    }
}
