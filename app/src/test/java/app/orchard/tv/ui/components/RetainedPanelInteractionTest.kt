package app.orchard.tv.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A retained Control Center must not steal focus or accept clicks after it closes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
class RetainedPanelInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun hiddenPanelRejectsFocusAndTouchesAndWorksAgainWhenOpened() {
        val open = mutableStateOf(false)
        val home = FocusRequester()
        val panel = FocusRequester()
        var clicks = 0
        val host = Robolectric.buildActivity(ComponentActivity::class.java)
        try {
            compose.runOnUiThread {
                host.setup().get().setContent {
                    Row {
                        Pressable({}, Modifier.testTag("home"), focusRequester = home) { Box(Modifier.size(80.dp)) }
                        CompositionLocalProvider(LocalPressableEnabled provides open.value) {
                            Pressable({ clicks++ }, Modifier.testTag("panel"), focusRequester = panel) { Box(Modifier.size(80.dp)) }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.runOnIdle { home.requestFocus(); panel.requestFocus() }
            compose.onNodeWithTag("home").assertIsFocused()
            compose.onNodeWithTag("panel").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused)).performTouchInput { click() }
            compose.runOnIdle { assertEquals(0, clicks); open.value = true }
            compose.waitForIdle()
            compose.onNodeWithTag("panel").performTouchInput { click() }
            compose.runOnIdle { assertEquals(1, clicks); open.value = false }
            compose.waitForIdle()
            compose.runOnIdle { home.requestFocus(); panel.requestFocus() }
            compose.onNodeWithTag("home").assertIsFocused()
        } finally { compose.runOnUiThread { host.close() } }
    }
}
