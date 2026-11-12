package app.orchard.tv.ui.home

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuizBankTest {
    @Test fun roundsAvoidPreviouslySeenQuestionsAndPreserveAnswers() {
        val seen = mutableSetOf<String>()
        repeat(20) { round ->
            val questions = QuizBank.round(seen, Random(round))
            assertEquals(5, questions.size)
            questions.forEach { q ->
                assertFalse(q.prompt in seen)
                assertEquals(q.choices.size, q.choices.distinct().size)
                assertTrue(q.answers.isNotEmpty())
                assertTrue(q.answers.all { it in q.choices.indices })
                if (q.prompt.contains(" + ")) {
                    val nums = Regex("\\d+").findAll(q.prompt).map { it.value.toInt() }.toList()
                    assertEquals((nums[0] + nums[1]).toString(), q.choices[q.answers.single()])
                }
            }
            seen.addAll(questions.map { it.prompt })
        }
    }
    @Test fun correctAnswersUseAllPositions() {
        val positions = mutableSetOf<Int>()
        repeat(50) { seed ->
            QuizBank.round(emptySet(), Random(seed)).filter { it.answers.size == 1 && it.choices.size == 4 }
                .forEach { positions.add(it.answers.single()) }
        }
        assertEquals(setOf(0, 1, 2, 3), positions)
    }
}
