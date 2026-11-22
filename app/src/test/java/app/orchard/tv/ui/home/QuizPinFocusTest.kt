package app.orchard.tv.ui.home

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.KidsLock
import app.orchard.tv.settings.Restricted
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real native quiz + Compose PIN integration without a device or emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
class QuizPinFocusTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun parentPinOwnsRemoteAfterSubmittingAnAnswerAndAfterCanceling() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java)
        val pinOpen = mutableStateOf(false)
        var verified = 0
        try {
            compose.runOnUiThread {
                val activity = host.setup().get()
                AerialSettings.init(activity)
                Restricted.init(activity)
                AerialSettings.choosePin("1111")
                activity.setContent {
                    Box {
                        KidsLockScreen(KidsLock.Break(Long.MAX_VALUE), pinOpen.value,
                            onParent = { pinOpen.value = true }, onLeave = {})
                        PinPad(pinOpen.value, if (pinOpen.value) PinMode.Verify else null,
                            onDone = { verified++; pinOpen.value = false }, onCancel = { pinOpen.value = false })
                    }
                }
            }
            compose.waitForIdle()
            val root = host.get().window.decorView
            val quiz = findQuiz(root)!!
            fun key(code: Int) {
                compose.runOnIdle {
                    root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                }
                compose.waitForIdle()
            }
            repeat(3) { round ->
                compose.runOnIdle { quiz.findViewWithTag<View>("answer-0").performClick() }
                if (round > 0) {
                    compose.runOnIdle { quiz.findViewWithTag<View>("submit").performClick() }
                    compose.waitForIdle()
                }
                compose.runOnIdle { quiz.findViewWithTag<View>("parent").performClick() }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertFalse(quiz.hasFocus())
                    quiz.onWindowFocusChanged(true)
                    assertFalse(quiz.findViewWithTag<View>("answer-0").requestFocus())
                }
                if (round == 0) {
                    key(KeyEvent.KEYCODE_BACK)
                    compose.runOnIdle { assertFalse(pinOpen.value); assertTrue(quiz.hasFocus()) }
                } else {
                    repeat(4) { key(KeyEvent.KEYCODE_DPAD_CENTER) }
                    compose.runOnIdle { assertFalse("PIN must accept four remote OK presses", pinOpen.value) }
                }
            }
            assertEquals(2, verified)
        } finally { compose.runOnUiThread { host.close() } }
    }

    private fun findQuiz(view: View): QuizPanel? {
        if (view is QuizPanel) return view
        if (view is ViewGroup) for (n in 0 until view.childCount) findQuiz(view.getChildAt(n))?.let { return it }
        return null
    }
}
