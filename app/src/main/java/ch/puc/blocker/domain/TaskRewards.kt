package ch.puc.blocker.domain

/** Repeating tasks. */
object Recurrence {
    /** Next due day of a weekly task: a week after [dueEpochDay], skipping weeks already past [today]. */
    fun nextWeekly(dueEpochDay: Long, today: Long): Long {
        var d = dueEpochDay + 7
        while (d < today) d += 7
        return d
    }
}

/** Pure reward math: finished task estimates buy short app unlocks. */
object TaskRewards {
    const val COST_MIN = 120              // 2 hours of finished tasks…
    const val UNLOCK_MS = 10 * 60_000L    // …buy a 10-minute unlock of one app

    data class Task(val estimateMin: Int, val createdMs: Long, val doneMs: Long)

    /** A task earns credit only if it sat on the list at least as long as its estimate (no instant check-offs). */
    fun counts(t: Task) = t.doneMs > 0 && t.doneMs - t.createdMs >= t.estimateMin * 60_000L

    fun earnedMin(tasks: List<Task>) = tasks.filter(::counts).sumOf { it.estimateMin }

    fun balanceMin(earnedMin: Int, spentMin: Int) = (earnedMin - spentMin).coerceAtLeast(0)

    fun canRedeem(balanceMin: Int) = balanceMin >= COST_MIN
}
