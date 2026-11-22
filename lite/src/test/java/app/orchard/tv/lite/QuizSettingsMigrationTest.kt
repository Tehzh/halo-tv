package app.orchard.tv.lite

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
    @After fun reset() { ReflectionHelpers.setStaticField(Kids::class.java, "started", false) }
    @Test fun legacyTimingRemainsAndNewSelectionsPersist() {
        val prefs = context.getSharedPreferences("restricted", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("break_every", 45).putInt("quiz_reward_max", 15).commit()
        ReflectionHelpers.setStaticField(Kids::class.java, "started", false)
        Kids.init(context)
        assertEquals(45, Kids.rules.breakEvery); assertEquals(15, Kids.quizRewardMax)
        assertEquals(0, Kids.quizStage); assertEquals(0, Kids.quizSubject)
        Kids.chooseQuizStage(3); Kids.chooseQuizSubject(2)
        ReflectionHelpers.setStaticField(Kids::class.java, "started", false)
        Kids.init(context)
        assertEquals(3, Kids.quizStage); assertEquals(2, Kids.quizSubject)
        assertEquals(45, Kids.rules.breakEvery); assertEquals(15, Kids.quizRewardMax)
    }
}
