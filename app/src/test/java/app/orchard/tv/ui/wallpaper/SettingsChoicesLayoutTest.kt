package app.orchard.tv.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
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
class SettingsChoicesLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    @After fun close() { compose.runOnUiThread { host?.close() } }
    @Test fun focusAndSoundChoicesFitWithoutHorizontalScrolling() {
        var density = 1f
        val focus = listOf(R.string.st_focus_high, R.string.st_focus_standard, R.string.focus_liquid_slide, R.string.focus_classic_glass_slide)
        val sound = listOf(R.string.st_sounds_tv, R.string.st_sounds_aerial, R.string.sound_soft, R.string.sound_crystal, R.string.st_off)
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity = host!!.get(); density = activity.resources.displayMetrics.density
            activity.setContent {
                Column(Modifier.width(384.dp)) {
                    Choices(R.string.st_focus_style, null, {}, focus.mapIndexed { i, id -> i to id }, 0, {})
                    Choices(R.string.st_sounds, null, {}, sound.mapIndexed { i, id -> i to id }, 0, {}, reserveCheck = true)
                }
            }
        }
        compose.waitForIdle()
        val context = host!!.get()
        for (id in focus + sound) {
            val bounds = compose.onNodeWithText(context.getString(id)).fetchSemanticsNode().boundsInRoot
            assertTrue("choice $id exceeds settings width: $bounds", bounds.left >= 0f && bounds.right <= 384f * density + 1f)
            assertTrue("choice $id falls below the first screen: $bounds", bounds.bottom <= 410f * density)
        }
    }
}
