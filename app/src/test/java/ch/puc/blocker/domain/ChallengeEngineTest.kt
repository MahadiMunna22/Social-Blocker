package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChallengeEngineTest {
    private var now = 1_000_000L
    private fun engine() = ChallengeEngine(Random(42), { now })

    private val bank = (1..30).map { BankItem("v:$it", BankKind.VOCAB, "A2", "der Wort$it", "word $it") } +
        (1..30).map { BankItem("g:$it", BankKind.GRAMMAR, "B1", "Satz $it ___ hier.", "a$it", listOf("a$it", "b$it", "c$it", "d$it")) }

    @Test fun quizHasTenQuestionsWithValidOptions() {
        val qs = engine().germanQuiz(bank)
        assertEquals(10, qs.size)
        assertEquals(10, qs.map { it.id }.distinct().size)
        qs.forEach { assertEquals(4, it.options.distinct().size); assertTrue(it.answer in it.options) }
    }

    @Test fun mixesVocabAndGrammarEvenly() {
        val qs = engine().germanQuiz(bank)
        assertEquals(5, qs.count { it.id!!.startsWith("v:") })
        assertEquals(5, qs.count { it.id!!.startsWith("g:") })
    }

    @Test fun prefersQuestionsNotAskedYet() {
        val asked = bank.map { if (it.id in setOf("v:1", "v:2", "g:1")) it.copy(timesAsked = 1, lastAskedMs = 5) else it }
        val ids = engine().germanQuiz(asked).map { it.id }
        assertFalse(ids.any { it in setOf("v:1", "v:2", "g:1") })
    }

    @Test fun failedQuestionsComeBackCappedAtThree() {
        val failed = bank.map { if (it.id in setOf("v:1", "v:2", "g:1", "g:2", "g:3")) it.copy(timesAsked = 1, lastAskedMs = 5, failed = true) else it }
        val ids = engine().germanQuiz(failed).map { it.id }
        assertEquals(3, ids.count { it in setOf("v:1", "v:2", "g:1", "g:2", "g:3") })
    }

    @Test fun grammarSolutionFillsTheBlank() {
        val q = engine().germanQuiz(bank).first { it.id!!.startsWith("g:") }
        assertEquals(q.prompt.replace("___", q.answer), q.solution)
    }

    @Test fun mathAnswersAreCorrect() {
        val e = engine()
        (1..3).forEach { level ->
            e.mathQuiz(level).forEach { q ->
                val expr = q.prompt.removeSuffix(" = ?").split(" ")
                val (a, op, b) = Triple(expr[0].toInt(), expr[1], expr[2].toInt())
                val expected = when (op) { "+" -> a + b; "−" -> a - b; "×" -> a * b; else -> a / b }
                assertEquals(expected.toString(), q.answer)
                assertTrue(q.answer in q.options)
            }
        }
    }

    @Test fun eightOfTenPassesAndUnlocksForWindow() {
        val e = engine()
        val qs = e.germanQuiz(bank)
        val answers = qs.map { it.answer }.toMutableList<String?>().also { it[0] = "x"; it[1] = "x" } // 8/10
        val outcome = e.submit(qs, answers)
        assertTrue(outcome.passed)
        assertEquals(listOf(false, false) + List(8) { true }, outcome.correct)
        assertTrue(e.isUnlocked())
        now += 5 * 60_000
        assertFalse(e.isUnlocked())
    }

    @Test fun sevenOfTenFailsAndStartsRetryCooldown() {
        val e = engine()
        val qs = e.germanQuiz(bank)
        val outcome = e.submit(qs, qs.map { it.answer }.toMutableList<String?>().also { it[0] = "x"; it[1] = "x"; it[2] = "x" })
        assertFalse(outcome.passed)
        assertEquals(3, outcome.corrections.size)
        assertEquals(60_000, e.cooldownRemainingMs())
        now += 60_000
        assertEquals(0, e.cooldownRemainingMs())
    }

    @Test(expected = IllegalStateException::class)
    fun submitDuringCooldownThrows() {
        val e = engine()
        val qs = e.mathQuiz(1)
        e.submit(qs, List(qs.size) { "x" })
        e.submit(qs, qs.map { it.answer })
    }

    @Test fun lockEndsWindow() {
        val e = engine()
        val qs = e.mathQuiz(1)
        e.submit(qs, qs.map { it.answer })
        e.lock()
        assertFalse(e.isUnlocked())
    }

    @Test fun waitUnlocksAfterTenMinutes() {
        val e = engine()
        e.startWait()
        assertEquals(WaitState.Waiting(10 * 60_000), e.waitState())
        assertFalse(e.claimWait())
        now += 10 * 60_000
        assertEquals(WaitState.Ready, e.waitState())
        assertTrue(e.claimWait())
        assertTrue(e.isUnlocked())
        assertEquals(WaitState.None, e.waitState())
    }

    @Test fun unclaimedWaitExpires() {
        val e = engine()
        e.startWait()
        now += 10 * 60_000 + 15 * 60_000
        assertEquals(WaitState.None, e.waitState())
        assertFalse(e.claimWait())
    }

    @Test fun cancelWaitClearsIt() {
        val e = engine()
        e.startWait()
        e.cancelWait()
        assertEquals(WaitState.None, e.waitState())
    }
}
