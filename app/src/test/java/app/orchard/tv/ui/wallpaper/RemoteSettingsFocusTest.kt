package app.orchard.tv.ui.wallpaper

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-w960dp-h540dp-land-xhdpi")
class RemoteSettingsFocusTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    @After fun close() { compose.runOnUiThread { host?.close() } }
    @Test fun rightFromRemoteCategoryEntersFirstFocusStyle() {
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent {
                val first = remember { FocusRequester() }
                val sidebar = remember { FocusRequester() }
                Row {
                    SettingsSidebar(4, -1, first, sidebar, onRecent = {}, onSelect = {})
                    Column(Modifier.width(384.dp)) {
                        Choices(R.string.st_focus_style, null, {}, listOf(0 to R.string.st_focus_high, 1 to R.string.st_focus_standard, 2 to R.string.focus_liquid_slide, 3 to R.string.focus_classic_glass_slide), 0, {}, firstFocusRequester = first, firstBackFocusRequester = sidebar)
                        Choices(R.string.st_sounds, null, {}, listOf(0 to R.string.st_sounds_tv, 1 to R.string.st_sounds_aerial), 0, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        val context = host!!.get()
        compose.onNodeWithText(context.getString(R.string.halo_cat_remote)).performTouchInput { click() }
        fun key(code: Int) = compose.runOnUiThread {
            host!!.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            host!!.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused() and hasAnyDescendant(hasText(context.getString(R.string.st_focus_high))), useUnmergedTree = true).assertExists()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(isFocused() and hasAnyDescendant(hasText(context.getString(R.string.st_focus_standard))), useUnmergedTree = true).assertExists()
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNode(isFocused() and hasAnyDescendant(hasText(context.getString(R.string.st_focus_high))), useUnmergedTree = true).assertExists()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(isFocused() and hasAnyDescendant(hasText(context.getString(R.string.st_sounds_tv))), useUnmergedTree = true).assertExists()
        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNode(isFocused() and hasAnyDescendant(hasText(context.getString(R.string.st_focus_high))), useUnmergedTree = true).assertExists()
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNode(isFocused() and hasAnyDescendant(hasText(context.getString(R.string.halo_cat_remote))), useUnmergedTree = true).assertExists()
    }
}
