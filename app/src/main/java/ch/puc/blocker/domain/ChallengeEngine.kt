package ch.puc.blocker.domain

import kotlin.random.Random

/**
 * One multiple-choice question. [id] links back to the bank item (null for generated math),
 * [label] is shown above the prompt and [solution] in the corrections list.
 */
data class Question(
    val prompt: String,
    val options: List<String>,
    val answer: String,
    val id: String? = null,
    val label: String? = null,
    val solution: String = "$prompt → $answer",
)

enum class BankKind { VOCAB, ARTICLE, GRAMMAR }

/** A question-bank entry plus its history, so the engine can avoid repeats. */
data class BankItem(
    val id: String,
    val kind: BankKind,
    val level: String,
    val prompt: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val timesAsked: Int = 0,
    val lastAskedMs: Long = 0,
    val failed: Boolean = false,
)

/** Alternative to the quiz: wait out a delay, then claim the unlock within a grace period. */
sealed interface WaitState {
    data object None : WaitState
    data class Waiting(val remainingMs: Long) : WaitState
    data object Ready : WaitState
}

data class ChallengeOutcome(
    val score: Int, val total: Int, val passed: Boolean, val corrections: List<Question>,
    /** Per question, in quiz order: answered correctly? */
    val correct: List<Boolean> = emptyList(),
)

/**
 * Framework-free quiz logic: question generation, scoring, retry cooldown and unlock window.
 * [clock] and [random] are injectable for tests.
 */
class ChallengeEngine(
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    val questionCount: Int = 10,
    val passThreshold: Int = 8,
    /** At most this many previously failed questions come back in one quiz. */
    val maxReview: Int = 3,
    val retryCooldownMs: Long = 60_000,
    val unlockWindowMs: Long = 5 * 60_000,
    val waitMs: Long = 10 * 60_000,
    val waitClaimGraceMs: Long = 15 * 60_000,
    lastFailureMs: Long = 0,
    waitReadyAtMs: Long = 0,
) {
    private var lastFailure = lastFailureMs
    private var waitReadyAt = waitReadyAtMs
    private var unlockedUntil = 0L

    fun cooldownRemainingMs(): Long = (lastFailure + retryCooldownMs - clock()).coerceAtLeast(0)
    fun isUnlocked(): Boolean = clock() < unlockedUntil
    fun unlockedUntilMs(): Long = unlockedUntil
    fun lock() { unlockedUntil = 0 }

    /** Starts the waiting period and returns the time it completes (for persistence). */
    fun startWait(): Long = (clock() + waitMs).also { waitReadyAt = it }
    fun cancelWait() { waitReadyAt = 0 }
    fun waitReadyAtMs(): Long = waitReadyAt

    fun waitState(): WaitState {
        val now = clock()
        return when {
            waitReadyAt == 0L -> WaitState.None
            now < waitReadyAt -> WaitState.Waiting(waitReadyAt - now)
            now < waitReadyAt + waitClaimGraceMs -> WaitState.Ready
            else -> { waitReadyAt = 0; WaitState.None } // not claimed in time: start over
        }
    }

    /** Turns a finished wait into an unlock window. Returns false if the wait isn't done. */
    fun claimWait(): Boolean {
        if (waitState() != WaitState.Ready) return false
        waitReadyAt = 0
        unlockedUntil = clock() + unlockWindowMs
        return true
    }

    /**
     * Picks [questionCount] German questions: up to [maxReview] you got wrong before (oldest first),
     * then the least-asked items, split evenly between vocabulary and grammar/articles.
     * Items you answered correctly only return once everything else has been asked as often.
     */
    fun germanQuiz(bank: List<BankItem>): List<Question> {
        require(bank.size >= questionCount) { "Question bank too small" }
        val review = bank.filter { it.failed }.sortedBy { it.lastAskedMs }.take(maxReview)
        val fresh = bank.filterNot { it.failed }.shuffled(random).sortedWith(compareBy({ it.timesAsked }, { it.lastAskedMs }))
        val need = questionCount - review.size
        val vocab = fresh.filter { it.kind == BankKind.VOCAB }
        val grammar = fresh.filter { it.kind != BankKind.VOCAB }
        val picked = (vocab.take(need / 2) + grammar.take(need - need / 2)).toMutableList()
        if (picked.size < need) picked += fresh.filterNot { it in picked }.take(need - picked.size) // one pool ran short
        return (review + picked).shuffled(random).map { toQuestion(it, bank) }
    }

    private fun partOfSpeech(de: String, en: String) = when {
        de.startsWith("der ") || de.startsWith("die ") || de.startsWith("das ") -> 'n'
        en.startsWith("to ") -> 'v'
        else -> 'a'
    }

    private fun toQuestion(item: BankItem, bank: List<BankItem>): Question {
        val label = "${item.level} · " + when (item.kind) { BankKind.VOCAB -> "Vocabulary"; BankKind.ARTICLE -> "Article"; BankKind.GRAMMAR -> "Grammar" }
        return when (item.kind) {
            BankKind.GRAMMAR -> {
                // Fill each blank in turn; "je … desto" style answers cover two blanks.
                val parts = item.answer.split(" … ").toMutableList()
                val filled = if (item.prompt.contains("___")) {
                    var out = item.prompt
                    while (out.contains("___") && parts.isNotEmpty()) out = out.replaceFirst("___", parts.removeAt(0))
                    out
                } else item.answer
                Question(item.prompt, item.options.shuffled(random), item.answer, item.id, label, filled)
            }
            BankKind.ARTICLE -> Question("Which article? ___ ${item.prompt}", item.options, item.answer, item.id, label, "${item.answer} ${item.prompt}")
            BankKind.VOCAB -> {
                val pos = partOfSpeech(item.prompt, item.answer)
                val peers = bank.filter { it.kind == BankKind.VOCAB && it.id != item.id && partOfSpeech(it.prompt, it.answer) == pos }
                val toEnglish = random.nextBoolean()
                val answer = if (toEnglish) item.answer else item.prompt
                val distractors = peers.map { if (toEnglish) it.answer else it.prompt }.filter { it != answer }.distinct().shuffled(random).take(3)
                val prompt = if (toEnglish) "What does „${item.prompt}“ mean?" else "How do you say „${item.answer}“ in German?"
                Question(prompt, (distractors + answer).shuffled(random), answer, item.id, label, "${item.prompt} = ${item.answer}")
            }
        }
    }

    /** difficulty 1: +/- up to 20, 2: +/- up to 100 and x up to 12, 3: bigger x and exact division. */
    fun mathQuiz(difficulty: Int): List<Question> = List(questionCount) {
        val (text, result) = when (difficulty.coerceIn(1, 3)) {
            1 -> arith(20, listOf('+', '-'))
            2 -> if (random.nextBoolean()) arith(100, listOf('+', '-')) else mul(12)
            else -> if (random.nextBoolean()) mul(25) else div(15)
        }
        val options = mutableSetOf(result)
        while (options.size < 4) options += result + random.nextInt(1, 11) * if (random.nextBoolean()) 1 else -1
        Question("$text = ?", options.map(Int::toString).shuffled(random), result.toString())
    }

    private fun arith(max: Int, ops: List<Char>): Pair<String, Int> {
        val a = random.nextInt(1, max + 1); val b = random.nextInt(1, max + 1)
        return if (ops.random(random) == '+') "$a + $b" to a + b else "${maxOf(a, b)} − ${minOf(a, b)}" to maxOf(a, b) - minOf(a, b)
    }
    private fun mul(max: Int): Pair<String, Int> {
        val a = random.nextInt(2, max + 1); val b = random.nextInt(2, 13)
        return "$a × $b" to a * b
    }
    private fun div(max: Int): Pair<String, Int> {
        val b = random.nextInt(2, 13); val q = random.nextInt(2, max + 1)
        return "${b * q} ÷ $b" to q
    }

    /** Scores [answers] (same order as [questions]). Throws if still in the retry cooldown. */
    fun submit(questions: List<Question>, answers: List<String?>): ChallengeOutcome {
        check(cooldownRemainingMs() == 0L) { "Retry cooldown active" }
        val correct = questions.mapIndexed { i, q -> answers.getOrNull(i)?.trim() == q.answer }
        val score = correct.count { it }
        val passed = score >= passThreshold
        if (passed) unlockedUntil = clock() + unlockWindowMs else lastFailure = clock()
        return ChallengeOutcome(score, questions.size, passed, questions.filterIndexed { i, _ -> !correct[i] }, correct)
    }
}
