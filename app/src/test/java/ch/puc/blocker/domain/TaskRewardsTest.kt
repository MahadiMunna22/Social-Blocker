package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRewardsTest {
    private val h = 60 * 60_000L

    @Test fun onlyFinishedTasksThatWaitedTheirEstimateCount() {
        val tasks = listOf(
            TaskRewards.Task(60, createdMs = 0, doneMs = 2 * h),    // counts
            TaskRewards.Task(90, createdMs = 0, doneMs = 10_000),   // ticked off instantly: no credit
            TaskRewards.Task(30, createdMs = 0, doneMs = 0),        // not done
            TaskRewards.Task(60, createdMs = 0, doneMs = h),        // exactly its estimate: counts
        )
        assertEquals(120, TaskRewards.earnedMin(tasks))
    }

    @Test fun twoHoursBuyOneUnlock() {
        assertFalse(TaskRewards.canRedeem(TaskRewards.balanceMin(119, 0)))
        assertTrue(TaskRewards.canRedeem(TaskRewards.balanceMin(240, 120)))
        assertFalse(TaskRewards.canRedeem(TaskRewards.balanceMin(240, 240)))
    }

    @Test fun balanceNeverNegative() {
        assertEquals(0, TaskRewards.balanceMin(60, 120))
    }
}
