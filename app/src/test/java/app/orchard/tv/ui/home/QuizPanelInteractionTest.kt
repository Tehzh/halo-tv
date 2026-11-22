package app.orchard.tv.ui.home

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Restricted
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
class QuizPanelInteractionTest {
    private val host = Robolectric.buildActivity(Activity::class.java)
    private lateinit var quiz: QuizPanel
    private var parentCount = 0
    private var passedCount = 0

    @Before fun setUp() {
        val activity = host.setup().visible().get()
        AerialSettings.init(activity)
        Restricted.init(activity)
        AerialSettings.choosePin("1111")
        quiz = QuizPanel(activity, { passedCount++ }, { parentCount++ })
        activity.setContentView(quiz)
        layout()
    }
    @After fun tearDown() { host.close() }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun layout(w: Int = 960, h: Int = 540) {
        quiz.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        quiz.layout(0, 0, w, h)
        idle()
        quiz.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        quiz.layout(0, 0, w, h)
        idle()
    }
    private fun v(tag: String): View = quiz.findViewWithTag(tag)
    private fun key(code: Int) {
        quiz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        quiz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        idle()
    }
    private fun click(tag: String) { v(tag).requestFocus(); key(KeyEvent.KEYCODE_DPAD_CENTER) }

    @Test fun remoteCanSubmitAndReachParentOnEveryQuestion() {
        repeat(5) {
            val options = (0..3).mapNotNull { n -> quiz.findViewWithTag<View>("answer-$n") }
            assertFalse(options.isEmpty())
            options.first().requestFocus()
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            repeat((options.size + 1) / 2) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
            assertSame(v("submit"), quiz.findFocus())
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertSame(v("parent"), quiz.findFocus())
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            assertSame(v("submit"), quiz.findFocus())
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            layout()
        }
        assertNull(quiz.findViewWithTag<View>("answer-0"))
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, parentCount)
    }

    @Test fun focusedActionUsesLightFillAndDarkText() {
        click("answer-0")
        val submit = v("submit") as Button
        submit.requestFocus()
        assertEquals(0xffffe6a6.toInt(), (submit.background.current as GradientDrawable).color!!.defaultColor)
        assertEquals(0xff30234f.toInt(), submit.currentTextColor)
    }

    @Test fun navigationStaysInsideTheQuizAtEveryOuterEdge() {
        val first = v("answer-0")
        first.requestFocus()
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertSame(first, quiz.findFocus())
        val parent = v("parent")
        parent.requestFocus()
        for (code in listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            key(code)
            assertSame(parent, quiz.findFocus())
        }
        click("answer-0")
        val submit = v("submit")
        submit.requestFocus()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertSame(submit, quiz.findFocus())
    }

    @Test fun suspendedQuizCannotTakeFocusOrChangeAnswersEvenAfterResize() {
        click("answer-0")
        quiz.setInteractionEnabled(false)
        assertFalse(v("answer-1").requestFocus())
        v("answer-1").performClick()
        quiz.onWindowFocusChanged(true)
        layout(1280, 720)
        assertFalse(quiz.hasFocus())
        assertTrue(v("answer-0").isSelected)
        assertFalse(v("answer-1").isSelected)
        quiz.setInteractionEnabled(true)
        idle()
        assertTrue(quiz.hasFocus())
        assertTrue(v("submit").isEnabled)
    }

    @Test fun cancelNativePinKeepsSelectedAnswerAndReturnsToParent() {
        click("answer-0")
        quiz.showParentPin { fail("PIN was not entered") }
        layout()
        key(KeyEvent.KEYCODE_BACK)
        layout()
        assertTrue(v("answer-0").isSelected)
        assertTrue(v("submit").isEnabled)
        assertSame(v("parent"), quiz.findFocus())
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertSame(v("submit"), quiz.findFocus())
    }

    @Test fun nativeKeypadRecoversFromWrongPinAndAcceptsRemoteOnlyEntryOnce() {
        var unlocked = 0
        quiz.showParentPin { unlocked++ }
        layout()
        repeat(4) { key(KeyEvent.KEYCODE_2) }
        assertEquals(0, unlocked)
        assertSame(v("pin-1"), quiz.findFocus())
        repeat(4) { key(KeyEvent.KEYCODE_DPAD_CENTER) }
        assertEquals(1, unlocked)
        repeat(4) { key(KeyEvent.KEYCODE_1) }
        assertEquals(1, unlocked)
    }

    @Test fun arrowPinUsesDirectionsAndSurvivesResize() {
        AerialSettings.choosePin("UDLR", arrows = true)
        var unlocked = 0
        quiz.showParentPin { unlocked++ }
        layout()
        key(KeyEvent.KEYCODE_DPAD_UP)
        layout(1280, 720)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(1, unlocked)
    }

    @Test fun canceledOrUnpairedReleaseCannotSubmitOrSelectNextQuestion() {
        click("answer-0")
        val submit = v("submit")
        submit.requestFocus()
        quiz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        quiz.dispatchKeyEvent(KeyEvent(0, 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0, 0, 0, KeyEvent.FLAG_CANCELED))
        assertSame(submit, v("submit"))
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        layout()
        assertNotSame(submit, v("submit"))
        quiz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(v("answer-0").isSelected)
        quiz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        quiz.showParentPin { fail("old press entered PIN") }
        layout()
        quiz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        repeat(3) { key(KeyEvent.KEYCODE_DPAD_CENTER) }
        assertTrue(quiz.findViewWithTag<View>("pin-1").isEnabled)
    }
}
