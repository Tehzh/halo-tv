package app.orchard.tv.ui.components

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
class PressableCancelledTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun cancelledReleaseDoesNotClickButNormalReleaseDoes() {
        val focus = FocusRequester()
        val host = Robolectric.buildActivity(ComponentActivity::class.java)
        var clicks = 0
        try {
            compose.runOnUiThread {
                host.setup()
                host.get().setContent { Pressable(onClick = { clicks++ }, focusRequester = focus) { Box(Modifier.size(60.dp)) } }
            }
            compose.waitForIdle()
            compose.runOnIdle {
                focus.requestFocus()
                val view = host.get().window.decorView
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                view.dispatchKeyEvent(KeyEvent(0, 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0, 0, 0, KeyEvent.FLAG_CANCELED))
                assertEquals(0, clicks)
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
                assertEquals(1, clicks)
            }
        } finally { compose.runOnUiThread { host.close() } }
    }
}
