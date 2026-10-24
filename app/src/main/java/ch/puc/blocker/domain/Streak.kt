package ch.puc.blocker.domain

import java.time.LocalDate

/** Days in a row, up to today, on which no app reached its daily cap (Loop-style streak). */
object Streak {
    /**
     * [usedMs] maps a day to each app's time that day, [capsMs] each limited app's daily cap.
     * Days with no data count as good, but nothing before [since] (when tracking began) counts.
     */
    fun days(usedMs: Map<LocalDate, Map<String, Long>>, capsMs: Map<String, Long>, today: LocalDate, since: LocalDate): Int {
        fun good(d: LocalDate) = usedMs[d].orEmpty().none { (pkg, ms) -> capsMs[pkg]?.let { ms >= it } == true }
        var n = 0
        var d = today
        while (!d.isBefore(since) && good(d)) { n++; d = d.minusDays(1) }
        return n
    }
}
