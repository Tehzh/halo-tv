package app.orchard.tv.lite

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HaloWelcomeLiteTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("first_run_guide", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun upgradeKeepsPreviousWelcomeCompletion() {
        context.getSharedPreferences("first_run_guide", Context.MODE_PRIVATE).edit()
            .putBoolean("finished", true).putBoolean("welcome3_completed", true).commit()
        assertFalse(HaloWelcomeView.shouldShow(context))
    }
    @Test fun freshInstallStillShowsWelcome() {
        assertTrue(HaloWelcomeView.shouldShow(context))
    }
    @Test fun unfinishedWelcomeAndOldTwoPointZeroGuideCannotCompleteOnboarding() {
        context.getSharedPreferences("first_run_guide", Context.MODE_PRIVATE).edit()
            .putBoolean("finished", true).putBoolean("welcome3_completed", false).commit()
        assertTrue(HaloWelcomeView.shouldShow(context))
    }
    @Test fun upgradeCompletionSurvivesRemovalOfPreviousDesignFlag() {
        context.getSharedPreferences("first_run_guide", Context.MODE_PRIVATE).edit()
            .putBoolean("welcome3_completed", true).commit()
        assertFalse(HaloWelcomeView.shouldShow(context))
        context.getSharedPreferences("first_run_guide", Context.MODE_PRIVATE).edit()
            .remove("welcome3_completed").commit()
        assertFalse(HaloWelcomeView.shouldShow(context))
    }
    @Test fun completionPersistsAcrossNewViewInstances() {
        var completed = 0
        val welcome = HaloWelcomeView(context) { completed++ }
        welcome.performClick(); welcome.performClick()
        assertEquals(1, completed)
        assertFalse(HaloWelcomeView.shouldShow(context))
    }
    @Test fun releaseFromPreviousScreenDoesNotAccidentallyDismiss() {
        var completed = false
        val welcome = HaloWelcomeView(context) { completed = true }
        welcome.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(completed)
        press(welcome)
        assertTrue(completed)
    }
    @Test fun firstConfirmationEntersImmediatelyAfterAttach() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        var completed = 0
        val welcome = HaloWelcomeView(controller.get()) { completed++ }
        controller.get().setContentView(welcome)
        assertTrue(HaloWelcomeView.shouldShow(context))
        press(welcome)
        assertEquals(1, completed)
        assertFalse(HaloWelcomeView.shouldShow(context))
        controller.pause().stop().destroy()
    }
    @Test fun detachReleasesAllArtworkWithoutCompletingWelcome() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val welcome = HaloWelcomeView(controller.get()) {}
        controller.get().setContentView(welcome)
        assertNotNull(ReflectionHelpers.getField<Any?>(welcome, "backgroundArt"))
        controller.get().setContentView(View(controller.get()))
        for (field in listOf("backgroundArt", "handwriting", "logo"))
            assertNull(ReflectionHelpers.getField<Any?>(welcome, field))
        assertTrue(HaloWelcomeView.shouldShow(context))
        controller.pause().stop().destroy()
    }
    private fun press(welcome: HaloWelcomeView) {
        welcome.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        welcome.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
