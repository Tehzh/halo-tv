package app.orchard.tv.settings

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizSettingsMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    @After fun reset() { ReflectionHelpers.setStaticField(Restricted::class.java, "started", false) }
    @Test fun legacyTimingRemainsAndNewSelectionsPersist() {
        val prefs = context.getSharedPreferences("restricted", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("break_every", 45).putInt("quiz_reward_max", 15).commit()
        ReflectionHelpers.setStaticField(Restricted::class.java, "started", false)
        Restricted.init(context)
        assertEquals(45, Restricted.rules.breakEvery); assertEquals(15, Restricted.quizRewardMax)
        assertEquals(0, Restricted.quizStage); assertEquals(0, Restricted.quizSubject)
        Restricted.chooseQuizStage(3); Restricted.chooseQuizSubject(2)
        ReflectionHelpers.setStaticField(Restricted::class.java, "started", false)
        Restricted.init(context)
        assertEquals(3, Restricted.quizStage); assertEquals(2, Restricted.quizSubject)
        assertEquals(45, Restricted.rules.breakEvery); assertEquals(15, Restricted.quizRewardMax)
    }
}
