package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val caps = mapOf("insta" to 60L)

    @Test fun countsBackToTheLastOverDay() {
        val used = mapOf(
            today to mapOf("insta" to 10L),
            today.minusDays(1) to mapOf("insta" to 59L),
            today.minusDays(2) to mapOf("insta" to 60L), // hit the cap
        )
        assertEquals(2, Streak.days(used, caps, today, since = today.minusDays(20)))
    }

    @Test fun overTodayBreaksIt_andNothingBeforeTrackingCounts() {
        assertEquals(0, Streak.days(mapOf(today to mapOf("insta" to 90L)), caps, today, today.minusDays(5)))
        assertEquals(3, Streak.days(emptyMap(), caps, today, since = today.minusDays(2)))
    }

    @Test fun unlimitedAppsDontBreakIt() {
        assertEquals(1, Streak.days(mapOf(today to mapOf("other" to 999L)), caps, today, today))
    }
}
