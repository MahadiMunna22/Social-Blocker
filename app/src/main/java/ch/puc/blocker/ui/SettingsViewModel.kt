package ch.puc.blocker.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.puc.blocker.BlockerApp
import ch.puc.blocker.Day
import ch.puc.blocker.data.ChallengeResult
import ch.puc.blocker.data.ChallengeType
import ch.puc.blocker.data.ChangeLog
import ch.puc.blocker.data.GlobalSettings
import ch.puc.blocker.data.QuizItem
import ch.puc.blocker.data.RewardUnlock
import ch.puc.blocker.data.TaskItem
import ch.puc.blocker.data.TrackedApp
import ch.puc.blocker.data.UnlockWait
import ch.puc.blocker.domain.BankItem
import ch.puc.blocker.domain.BankKind
import ch.puc.blocker.domain.ChallengeEngine
import ch.puc.blocker.domain.ChallengeOutcome
import ch.puc.blocker.domain.Decision
import ch.puc.blocker.domain.Question
import ch.puc.blocker.domain.Reason
import ch.puc.blocker.domain.RuleEngine
import ch.puc.blocker.domain.TaskRewards
import ch.puc.blocker.domain.UsageState
import ch.puc.blocker.domain.WaitState
import ch.puc.blocker.overlay.BlockOverlay
import ch.puc.blocker.ruleConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AppRow(
    val app: TrackedApp,
    val usedMs: Long,
    val sessionMs: Long,
    val decision: Decision,
)

data class DayTotal(val date: LocalDate, val minutes: Long)

data class QuizState(
    val questions: List<Question>,
    val answers: List<String?>,
    val index: Int = 0,
    val outcome: ChallengeOutcome? = null,
)

/** Per-app timer values edited together in the UI. */
data class Limits(val session: Int, val cooldown: Int, val cap: Int) {
    companion object {
        val presets = listOf(
            "Light" to Limits(20, 15, 120),
            "Balanced" to Limits(10, 30, 60),
            "Strict" to Limits(5, 45, 30),
        )
    }
}

fun TrackedApp.limits() = Limits(sessionLimitMin, cooldownMin, dailyCapMin)

private fun QuizItem.toBankItem() = BankItem(
    id, BankKind.valueOf(kind), level, prompt, answer,
    if (options.isEmpty()) emptyList() else options.split("\n"), timesAsked, lastAskedMs, failed,
)

data class RewardState(
    val earnedMin: Int = 0,
    val spentMin: Int = 0,
    val balanceMin: Int = 0,
    /** Package → reward end time, for rewards running right now. */
    val active: Map<String, Long> = emptyMap(),
)

enum class UnlockStep { CHOOSE, QUIZ, WAIT }

data class Candidate(val packageName: String, val label: String, val suggested: Boolean)

/** Common social apps, surfaced first in the picker when installed. */
private val SUGGESTED = setOf(
    "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.twitter.android",
    "com.facebook.katana", "com.snapchat.android", "com.reddit.frontpage", "com.google.android.youtube",
    "com.pinterest", "com.linkedin.android", "com.instagram.barcelona", "com.bereal.ft",
)

private const val TICK_MS = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as BlockerApp).db.dao()
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** Ticks every second while the UI is visible: drives countdowns and the unlock window. */
    val now: StateFlow<Long> = flow { while (true) { emit(System.currentTimeMillis()); delay(TICK_MS) } }
        .state(System.currentTimeMillis())
    private val today = now.map { Day.today() }.distinctUntilChanged()

    val settings: StateFlow<GlobalSettings?> = dao.observeSettings().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Morning lockout end time if active right now. */
    val morningUntilMs: StateFlow<Long?> = combine(today.flatMapLatest { dao.observeDayState(it) }, settings, now) { d, s, t ->
        val until = d?.firstUnlockMs?.plus((s?.morningLockoutMin ?: 0) * 60_000L)
        until?.takeIf { t < it && s?.blockingEnabled == true }
    }.state(null)

    val rows: StateFlow<List<AppRow>> = combine(
        dao.observeApps(), today.flatMapLatest { dao.observeUsage(it) }, settings,
        today.flatMapLatest { dao.observeDayState(it) }, now,
    ) { apps, usage, s, day, t ->
        val gs = s ?: GlobalSettings()
        val byPkg = usage.associateBy { it.packageName }
        apps.map { app ->
            val u = byPkg[app.packageName]
            val state = UsageState(u?.totalMs ?: 0, u?.sessionMs ?: 0, u?.lastSeenMs ?: 0, u?.cooldownUntilMs ?: 0)
            val cfg = ruleConfig(app, gs)
            // A session that has been idle longer than the grace period is effectively over.
            val session = if (t - state.lastSeenMs > cfg.graceMs) 0 else state.sessionMs
            val decision = if (gs.blockingEnabled && app.enabled) RuleEngine.evaluate(t, day?.firstUnlockMs, Day.startOfTomorrowMs(), cfg, state) else Decision.Allow
            AppRow(app, state.totalMs, session, decision)
        }
    }.state(emptyList())

    /** Total tracked minutes for each of the last 7 days (oldest first). */
    val week: StateFlow<List<DayTotal>> = today.flatMapLatest { dao.observeUsageSince(Day.daysAgo(6)) }.map { list ->
        val byDate = list.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.totalMs } / 60_000 }
        (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }.map { DayTotal(it, byDate[it.toString()] ?: 0) }
    }.state(emptyList())

    val changes: StateFlow<List<ChangeLog>> = dao.observeChanges().state(emptyList())
    val results: StateFlow<List<ChallengeResult>> = dao.observeResults().state(emptyList())

    // --- Tasks & rewards ---

    val tasks: StateFlow<List<TaskItem>> = dao.observeTasks().state(emptyList())

    val rewards: StateFlow<RewardState> = combine(dao.observeTasks(), dao.observeRewards(), now) { tasks, redeemed, t ->
        val earned = TaskRewards.earnedMin(tasks.map { TaskRewards.Task(it.estimateMin, it.createdMs, it.doneMs) })
        val spent = redeemed.sumOf { it.costMin }
        RewardState(
            earnedMin = earned, spentMin = spent, balanceMin = TaskRewards.balanceMin(earned, spent),
            active = redeemed.filter { it.untilMs > t }.associate { it.packageName to it.untilMs },
        )
    }.state(RewardState())

    fun saveTask(task: TaskItem) = viewModelScope.launch { dao.upsertTask(task) }
    fun deleteTask(task: TaskItem) = viewModelScope.launch { dao.deleteTask(task) }
    fun toggleDone(task: TaskItem) = viewModelScope.launch {
        dao.upsertTask(task.copy(doneMs = if (task.doneMs == 0L) System.currentTimeMillis() else 0))
    }

    /** Spends 2 h of finished tasks on a 10-minute unlock of one app. Earned, so no quiz needed. */
    fun redeem(app: TrackedApp) = viewModelScope.launch {
        if (!TaskRewards.canRedeem(rewards.value.balanceMin)) return@launch
        val now = System.currentTimeMillis()
        dao.insertReward(RewardUnlock(packageName = app.packageName, startMs = now, untilMs = now + TaskRewards.UNLOCK_MS, costMin = TaskRewards.COST_MIN))
        log("Task reward", "-", "${app.label} unlocked 10 min")
    }

    private var engine: ChallengeEngine? = null
    var quiz by mutableStateOf<QuizState?>(null)
        private set
    var unlockedUntilMs by mutableStateOf(0L)
        private set
    /** Which unlock screen is showing, or null when the main UI is visible. */
    var unlockStep by mutableStateOf<UnlockStep?>(null)
        private set

    init {
        viewModelScope.launch {
            (application as BlockerApp).seeded.await()
            engine = ChallengeEngine(
                lastFailureMs = dao.lastFailureMs() ?: 0,
                waitReadyAtMs = dao.unlockWait()?.readyAtMs ?: 0,
            )
        }
    }

    fun isUnlocked(t: Long) = t < unlockedUntilMs
    fun cooldownRemainingMs(): Long = engine?.cooldownRemainingMs() ?: 0
    fun waitState(): WaitState = engine?.waitState() ?: WaitState.None
    val passThreshold get() = engine?.passThreshold ?: 8
    val questionCount get() = engine?.questionCount ?: 10
    val waitMinutes get() = (engine?.waitMs ?: 600_000) / 60_000

    // --- Unlock flow: choose, then quiz or wait ---

    /** Returns true if edits are allowed right now; otherwise opens the unlock flow. */
    fun requireUnlock(): Boolean {
        if (isUnlocked(System.currentTimeMillis())) return true
        openUnlock()
        return false
    }

    fun openUnlock() {
        unlockStep = if (waitState() != WaitState.None) UnlockStep.WAIT else UnlockStep.CHOOSE
    }

    fun closeUnlock() {
        unlockStep = null
        quiz = null
    }

    fun startChallenge() {
        val e = engine ?: return
        if (e.cooldownRemainingMs() > 0) return
        val s = settings.value ?: GlobalSettings()
        viewModelScope.launch {
            val qs = if (s.challengeType == ChallengeType.VOCAB) e.germanQuiz(dao.quizItems().map { it.toBankItem() })
            else e.mathQuiz(s.mathDifficulty)
            quiz = QuizState(qs, List(qs.size) { null })
            unlockStep = UnlockStep.QUIZ
        }
    }

    /** Records the answer for the current question (first choice counts). */
    fun answer(option: String) {
        val q = quiz ?: return
        if (q.answers[q.index] != null) return
        quiz = q.copy(answers = q.answers.toMutableList().also { it[q.index] = option })
    }

    fun next() {
        val q = quiz ?: return
        if (q.index < q.questions.lastIndex) quiz = q.copy(index = q.index + 1) else submit()
    }

    private fun submit() {
        val e = engine ?: return
        val q = quiz ?: return
        if (e.cooldownRemainingMs() > 0) return
        val outcome = e.submit(q.questions, q.answers)
        quiz = q.copy(outcome = outcome)
        unlockedUntilMs = e.unlockedUntilMs()
        val type = settings.value?.challengeType ?: ChallengeType.VOCAB
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.insertResult(ChallengeResult(timestampMs = now, type = type,
                score = outcome.score, total = outcome.total, passed = outcome.passed))
            // Remember each answer: wrong ones come back soon, right ones go to the back of the queue.
            q.questions.forEachIndexed { i, question ->
                question.id?.let { dao.recordAnswer(it, failed = !outcome.correct[i], now = now) }
            }
        }
    }

    /** Starts the no-quiz waiting period. It keeps running if the app is closed. */
    fun startWait() {
        val e = engine ?: return
        val readyAt = e.startWait()
        quiz = null
        unlockStep = UnlockStep.WAIT
        viewModelScope.launch { dao.upsertWait(UnlockWait(readyAtMs = readyAt)) }
    }

    fun cancelWait() {
        engine?.cancelWait()
        unlockStep = UnlockStep.CHOOSE
        viewModelScope.launch { dao.clearWait() }
    }

    fun claimWait() {
        val e = engine ?: return
        if (!e.claimWait()) return
        unlockedUntilMs = e.unlockedUntilMs()
        closeUnlock()
        viewModelScope.launch {
            dao.clearWait()
            log("Unlocked by waiting", "locked", "unlocked")
        }
    }

    /** Called when the app leaves the foreground. A running wait is kept. */
    fun lock() {
        engine?.lock()
        unlockedUntilMs = 0
        closeUnlock()
    }

    // --- Gated mutations (each logs old/new values) ---

    private fun gated(block: suspend () -> Unit) {
        if (!isUnlocked(System.currentTimeMillis())) return
        viewModelScope.launch { block() }
    }

    private suspend fun log(what: String, old: Any?, new: Any?) {
        if (old != new) dao.insertChange(ChangeLog(timestampMs = System.currentTimeMillis(), what = what,
            oldValue = old.toString(), newValue = new.toString()))
    }

    fun updateApp(old: TrackedApp, new: TrackedApp) = gated {
        dao.upsertApp(new)
        log("${old.label}: session limit (min)", old.sessionLimitMin, new.sessionLimitMin)
        log("${old.label}: cooldown (min)", old.cooldownMin, new.cooldownMin)
        log("${old.label}: daily cap (min)", old.dailyCapMin, new.dailyCapMin)
    }

    /** Adding apps only makes things stricter, so it never needs the unlock. */
    fun addApps(picked: List<Candidate>, limits: Limits) = viewModelScope.launch {
        picked.forEach {
            dao.upsertApp(TrackedApp(it.packageName, it.label, limits.session, limits.cooldown, limits.cap))
            log("Tracked apps", "-", "+ ${it.label}")
        }
    }

    /** Making rules stricter never needs the unlock, only loosening them does. */
    fun setAppEnabled(app: TrackedApp, on: Boolean) {
        if (!on && !requireUnlock()) return
        viewModelScope.launch {
            dao.upsertApp(app.copy(enabled = on))
            log("${app.label}: limits", if (app.enabled) "on" else "off", if (on) "on" else "off")
        }
    }

    fun toggleBlocking() {
        val s = settings.value ?: return
        if (s.blockingEnabled && !requireUnlock()) return
        viewModelScope.launch {
            dao.upsertSettings(s.copy(blockingEnabled = !s.blockingEnabled))
            log("Blocking enabled", s.blockingEnabled, !s.blockingEnabled)
        }
    }

    fun removeApp(app: TrackedApp) = gated {
        dao.deleteApp(app)
        log("Tracked apps", app.label, "removed")
    }

    fun updateSettings(new: GlobalSettings) = gated {
        val old = dao.settings() ?: GlobalSettings()
        dao.upsertSettings(new)
        log("Blocking enabled", old.blockingEnabled, new.blockingEnabled)
        log("Morning lockout (min)", old.morningLockoutMin, new.morningLockoutMin)
        log("Session grace (s)", old.sessionGraceSec, new.sessionGraceSec)
        log("Challenge type", old.challengeType, new.challengeType)
        log("Math difficulty", old.mathDifficulty, new.mathDifficulty)
    }

    /** Shows the real block screen for 4 seconds so the overlay permission can be verified. */
    fun previewOverlay() {
        var job: Job? = null
        val overlay = BlockOverlay(getApplication()) { job?.cancel() } // "OK" ends the preview early
        job = viewModelScope.launch {
            try {
                repeat(4) {
                    overlay.show("Preview", Decision.Block(Reason.COOLDOWN, System.currentTimeMillis() + 5 * 60_000))
                    delay(TICK_MS)
                }
            } finally {
                overlay.hide()
            }
        }
    }

    /** Launchable apps not yet tracked, suggested social apps first. */
    fun candidates(): List<Candidate> {
        val app = getApplication<Application>()
        val pm = app.packageManager
        val tracked = rows.value.map { it.app.packageName }.toSet()
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { Candidate(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.activityInfo.packageName in SUGGESTED) }
            .filter { it.packageName !in tracked && it.packageName != app.packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<Candidate> { it.suggested }.thenBy { it.label.lowercase() })
    }
}
