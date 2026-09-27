package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val min = 60_000L
    private val cfg = RuleConfig(morningLockoutMs = 30 * min, sessionLimitMs = 10 * min, cooldownMs = 30 * min, dailyCapMs = 60 * min, graceMs = 90_000)
    private val eod = 1_000_000_000L

    /** Simulates [ms] of continuous use from [start] in 1 s ticks. */
    private fun use(s: UsageState, start: Long, ms: Long): UsageState {
        var st = s
        var t = start
        while (t < start + ms) { t += 1_000; st = RuleEngine.accrue(st, t, 1_000, cfg) }
        return st
    }

    @Test fun allowsWhenNothingActive() {
        assertEquals(Decision.Allow, RuleEngine.evaluate(100 * min, 0, eod, cfg, UsageState()))
    }

    @Test fun morningLockout() {
        val d = RuleEngine.evaluate(10 * min, 0, eod, cfg, UsageState())
        assertEquals(Decision.Block(Reason.MORNING, 30 * min), d)
    }

    @Test fun sessionLimitStartsCooldown() {
        val s = use(UsageState(), 0, 10 * min)
        assertEquals(10 * min + 30 * min, s.cooldownUntilMs)
        assertEquals(0, s.sessionMs)
        val d = RuleEngine.evaluate(11 * min, null, eod, cfg, s) as Decision.Block
        assertEquals(Reason.COOLDOWN, d.reason)
    }

    @Test fun shortBackgroundingKeepsSession() {
        var s = use(UsageState(), 0, 5 * min)
        s = use(s, 5 * min + 60_000, 1_000) // 60 s gap < 90 s grace
        assertTrue(s.sessionMs > 5 * min)
    }

    @Test fun longBackgroundingResetsSession() {
        var s = use(UsageState(), 0, 5 * min)
        s = use(s, 5 * min + 3 * min, 1_000)
        assertEquals(1_000, s.sessionMs)
        assertEquals(5 * min + 1_000, s.totalMs)
    }

    @Test fun dailyCapBlocksUntilEndOfDay() {
        val d = RuleEngine.evaluate(100 * min, null, eod, cfg, UsageState(totalMs = 60 * min))
        assertEquals(Decision.Block(Reason.DAILY_CAP, eod), d)
    }

    @Test fun strictestRuleWins() {
        val s = UsageState(totalMs = 60 * min, cooldownUntilMs = 40 * min)
        val d = RuleEngine.evaluate(20 * min, 0, eod, cfg, s) as Decision.Block
        assertEquals(Reason.DAILY_CAP, d.reason)
    }

    @Test fun remainingPicksSmallerAllowance() {
        assertEquals(Remaining(4 * min, dailyLimited = false), RuleEngine.remaining(cfg, UsageState(totalMs = 20 * min, sessionMs = 6 * min)))
        assertEquals(Remaining(2 * min, dailyLimited = true), RuleEngine.remaining(cfg, UsageState(totalMs = 58 * min, sessionMs = 1 * min)))
    }
}
