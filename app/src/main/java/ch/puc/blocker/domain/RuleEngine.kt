package ch.puc.blocker.domain

private const val MIN = 60_000L

/** Rule values for one app (global morning lockout included). */
data class RuleConfig(
    val morningLockoutMs: Long = 30 * MIN,
    val sessionLimitMs: Long = 10 * MIN,
    val cooldownMs: Long = 30 * MIN,
    val dailyCapMs: Long = 60 * MIN,
    val graceMs: Long = 90_000,
)

data class UsageState(
    val totalMs: Long = 0,
    val sessionMs: Long = 0,
    val lastSeenMs: Long = 0,
    val cooldownUntilMs: Long = 0,
)

enum class Reason { MORNING, COOLDOWN, DAILY_CAP }

sealed interface Decision {
    data object Allow : Decision
    data class Block(val reason: Reason, val untilMs: Long) : Decision
}

data class Remaining(val ms: Long, val dailyLimited: Boolean)

/** Pure rule logic. No Android types, so it can be unit tested directly. */
object RuleEngine {

    /** Adds [deltaMs] of foreground time. Starts a cooldown when the session limit is hit. */
    fun accrue(s: UsageState, now: Long, deltaMs: Long, cfg: RuleConfig): UsageState {
        val gapExceeded = s.lastSeenMs > 0 && now - s.lastSeenMs - deltaMs > cfg.graceMs
        val session = (if (gapExceeded) 0 else s.sessionMs) + deltaMs
        return if (session >= cfg.sessionLimitMs) {
            s.copy(totalMs = s.totalMs + deltaMs, sessionMs = 0, lastSeenMs = now, cooldownUntilMs = now + cfg.cooldownMs)
        } else {
            s.copy(totalMs = s.totalMs + deltaMs, sessionMs = session, lastSeenMs = now)
        }
    }

    /** Time until the next block while allowed: the smaller of the session and daily allowance. */
    fun remaining(cfg: RuleConfig, s: UsageState): Remaining {
        val session = (cfg.sessionLimitMs - s.sessionMs).coerceAtLeast(0)
        val daily = (cfg.dailyCapMs - s.totalMs).coerceAtLeast(0)
        return if (daily <= session) Remaining(daily, dailyLimited = true) else Remaining(session, dailyLimited = false)
    }

    /** Returns the strictest active block (latest unblock time), or Allow. */
    fun evaluate(now: Long, firstUnlockMs: Long?, endOfDayMs: Long, cfg: RuleConfig, s: UsageState): Decision {
        val blocks = buildList {
            firstUnlockMs?.let { val until = it + cfg.morningLockoutMs; if (now < until) add(Decision.Block(Reason.MORNING, until)) }
            if (now < s.cooldownUntilMs) add(Decision.Block(Reason.COOLDOWN, s.cooldownUntilMs))
            if (s.totalMs >= cfg.dailyCapMs) add(Decision.Block(Reason.DAILY_CAP, endOfDayMs))
        }
        return blocks.maxByOrNull { it.untilMs } ?: Decision.Allow
    }
}
