package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAggregatorTest {
    private fun r(p: String, t: Long) = FgEvent(p, true, t)
    private fun p(p: String, t: Long) = FgEvent(p, false, t)

    @Test fun sumsResumePausePairs() {
        val m = UsageAggregator.foregroundMs(listOf(r("a", 10), p("a", 30), r("a", 50), p("a", 60), r("b", 60), p("b", 65)), 0, 100)
        assertEquals(30L, m["a"])
        assertEquals(5L, m["b"])
    }

    @Test fun appOpenAtStartCountsFromWindowStart() {
        assertEquals(20L, UsageAggregator.foregroundMs(listOf(p("a", 20)), 0, 100)["a"])
    }

    @Test fun appStillOpenCountsUntilNow() {
        assertEquals(40L, UsageAggregator.foregroundMs(listOf(r("a", 60)), 0, 100)["a"])
    }

    @Test fun duplicateResumeIsNotDoubleCounted() {
        assertEquals(30L, UsageAggregator.foregroundMs(listOf(r("a", 10), r("a", 20), p("a", 40)), 0, 100)["a"])
    }
}
