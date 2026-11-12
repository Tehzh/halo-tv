package app.orchard.tv.ui.home

import android.content.Context
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HaloWelcomeTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("first_run_guide", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun upgradeFromOldCompletedGuideStillShowsNewWelcome() {
        context.getSharedPreferences("first_run_guide", Context.MODE_PRIVATE).edit().putBoolean("finished", true).commit()
        assertTrue(FirstRunGuideStore.shouldShow(context))
    }
    @Test fun completionPersistsAcrossNewViewInstances() {
        var completed = 0
        assertTrue(FirstRunGuideStore.shouldShow(context))
        val welcome = HaloWelcomeView(context) { completed++ }
        welcome.performClick(); welcome.performClick()
        assertEquals(1, completed)
        assertFalse(FirstRunGuideStore.shouldShow(context))
    }
    @Test fun releaseFromPreviousScreenDoesNotAccidentallyDismiss() {
        var completed = false
        val welcome = HaloWelcomeView(context) { completed = true }
        welcome.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(completed)
        welcome.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        welcome.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(completed)
        assertFalse(FirstRunGuideStore.shouldShow(context))
    }
}
