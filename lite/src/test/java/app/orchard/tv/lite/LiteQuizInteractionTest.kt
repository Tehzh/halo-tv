package app.orchard.tv.lite

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
class LiteQuizInteractionTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var quiz: LiteQuizSheet
    private var parents = 0
    @Before fun setup() {
        Ui.init(context)
        LiteSettings.init(context)
        Kids.init(context)
        LiteSettings.choosePin("1111")
        quiz = LiteQuizSheet(context, { parents++ }, {})
        layout()
    }
    private fun layout() {
        quiz.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        quiz.layout(0, 0, 960, 540)
    }
    private fun key(sheet: Sheet, code: Int) {
        sheet.onKey(KeyEvent(KeyEvent.ACTION_DOWN, code))
        sheet.onKey(KeyEvent(KeyEvent.ACTION_UP, code))
    }
    private fun controls(view: View = quiz): List<Control> = when (view) {
        is Control -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { controls(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun answerSelectionPreservesControlsAndFocusThenSubmitAdvancesOnce() {
        val before = controls()
        val first = quiz.focused
        repeat(9) {
            key(quiz, KeyEvent.KEYCODE_DPAD_CENTER)
            assertSame(first, quiz.focused)
            assertEquals(before, controls())
        }
        val lastRow = (before.size - 2) / 2
        quiz.focusFirst(lastRow, 0)
        key(quiz, KeyEvent.KEYCODE_DPAD_CENTER)
        layout()
        assertNotEquals(before, controls())
        assertEquals(0 to 0, quiz.position)
        val next = controls()
        quiz.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(next, controls())
        quiz.focusFirst((next.size - 2) / 2, 0)
        key(quiz, KeyEvent.KEYCODE_DPAD_RIGHT)
        key(quiz, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, parents)
    }

    @Test fun coveredQuizDropsOldPressAndPinSupportsOkOnlyRemote() {
        val first = quiz.focused!!
        quiz.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        quiz.setInteractionEnabled(false)
        assertFalse(first.focusedLook)
        assertFalse(quiz.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
        var verified = 0
        val pin = PinPad(context, {}, PinPad.Mode.Verify) { verified++ }
        repeat(4) { key(pin, KeyEvent.KEYCODE_DPAD_CENTER) }
        assertEquals(1, verified)
        quiz.setInteractionEnabled(true)
        assertSame(first, quiz.focused)
        assertTrue(first.focusedLook)
        quiz.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        quiz.focusFirst((controls().size - 2) / 2, 0)
        val unchanged = controls()
        key(quiz, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("Old key release must not have selected an answer", unchanged, controls())
    }

    @Test fun repeatedNumberKeyDoesNotFillPinWithOneHeldPress() {
        var verified = 0
        val pin = PinPad(context, {}, PinPad.Mode.Verify) { verified++ }
        pin.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1))
        repeat(5) { n -> pin.onKey(KeyEvent(0, 10L + n, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1, n + 1)) }
        pin.onKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_1))
        assertEquals(0, verified)
        repeat(3) { key(pin, KeyEvent.KEYCODE_1) }
        assertEquals(1, verified)
    }
}
