package app.orchard.tv.ui.home

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuizInventoryTest {
    @Test fun everyQuestionHasDistinctValidChoicesAndArithmeticIsCorrect() {
        val all = QuizBank.inventory()
        assertTrue(all.size > 1500)
        assertEquals(all.size, all.map { it.prompt }.distinct().size)
        val expression = Regex("^(\\d+) ([+−×÷]) (\\d+) = \\?$")
        val numberWords = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
        val wordExpression = Regex("^([a-z]+) ([+×]) ([a-z]+) = \\?$")
        all.forEach { q ->
            assertTrue(q.prompt, q.choices.size >= 2 && q.choices.size == q.choices.distinct().size)
            assertTrue(q.prompt, q.answers.isNotEmpty() && q.answers.all { it in q.choices.indices })
            expression.matchEntire(q.prompt)?.let { m ->
                val a = m.groupValues[1].toInt(); val b = m.groupValues[3].toInt()
                if (m.groupValues[2] == "+" || m.groupValues[2] == "×") assertTrue(q.prompt, a <= b)
                val result = when (m.groupValues[2]) { "+" -> a + b; "−" -> a - b; "×" -> a * b; else -> { assertEquals(0, a % b); a / b } }
                assertEquals(q.prompt, result.toString(), q.choices[q.answers.single()])
            }
            if (q.prompt.startsWith("请选择偶数")) {
                assertEquals(q.prompt, q.choices.indices.filter { q.choices[it].toInt() % 2 == 0 }.toSet(), q.answers)
            }
            Regex("^(\\d+) − 1 = (\\d+)，对吗？$").matchEntire(q.prompt)?.let { m ->
                val truth = m.groupValues[1].toInt() - 1 == m.groupValues[2].toInt()
                assertEquals(q.prompt, if (truth) "对" else "错", q.choices[q.answers.single()])
            }
            wordExpression.matchEntire(q.prompt)?.let { m ->
                val a = numberWords.indexOf(m.groupValues[1]); val b = numberWords.indexOf(m.groupValues[3]); assertTrue(a >= 0 && b >= 0)
                assertTrue(q.prompt, a <= b)
                val result = if (m.groupValues[2] == "+") a + b else a * b
                assertEquals(q.prompt, result.toString(), q.choices[q.answers.single()])
            }
        }
    }
    @Test fun reportCountsByStageAndSubject() {
        val all = QuizBank.inventory()
        for (stage in 1..4) for (subject in 1..3) {
            val count = all.count { (it.stage == 0 || it.stage == stage) && it.subject == subject }
            println("stage=$stage subject=$subject count=$count")
            assertTrue("stage=$stage subject=$subject", count >= 5)
        }
        for (subject in 1..3) println("subject=$subject total=${all.count { it.subject == subject }}")
    }
    @Test fun curatedVocabularyTranslationsMatchInBothDirections() {
        val all = QuizBank.inventory().filter { it.subject == 2 }
        val forward = Regex("^([a-z]+) 是什么意思？$")
        val reverse = Regex("^“(.+)”用英语怎么说？$")
        val meanings = all.mapNotNull { q -> forward.matchEntire(q.prompt)?.let { it.groupValues[1] to q.choices[q.answers.single()] } }.toMap()
        val reverseQuestions = all.mapNotNull { q -> reverse.matchEntire(q.prompt)?.let { it.groupValues[1] to q.choices[q.answers.single()] } }
        assertTrue(reverseQuestions.size >= 120)
        reverseQuestions.forEach { (meaning, word) -> assertEquals(word, meaning, meanings[word]) }
        for (stage in 1..4) {
            assertTrue(all.count { it.stage == stage } >= 90)
            assertTrue(QuizBank.inventory().count { it.subject == 3 && (it.stage == 0 || it.stage == stage) } >= 20)
        }
    }
    @Test fun everyFilterHasEnoughUniqueQuestionsAndHonoursHistory() {
        for (stage in 0..4) for (subject in 0..3) {
            val seen = mutableSetOf<String>()
            repeat(2) { n ->
                val round = QuizBank.round(seen, Random(stage * 100 + subject * 10 + n), stage, subject)
                assertEquals(5, round.size); assertEquals(5, round.map { it.prompt }.distinct().size)
                round.forEach { q ->
                    assertTrue(q.prompt, stage == 0 || q.stage == 0 || q.stage == stage)
                    assertTrue(q.prompt, subject == 0 || q.subject == subject)
                }
                val available = QuizBank.inventory().count { (stage == 0 || it.stage == 0 || it.stage == stage) && (subject == 0 || it.subject == subject) }
                if (available >= 10) assertTrue(round.none { it.prompt in seen })
                seen.addAll(round.map { it.prompt })
            }
        }
    }
}
