package ch.puc.blocker.domain

/** One activity resume/pause event, extracted from UsageEvents so the math stays framework-free. */
data class FgEvent(val packageName: String, val resumed: Boolean, val timeMs: Long)

/**
 * Sums foreground time per package in [fromMs, toMs] from resume/pause events.
 * This is the same source Digital Wellbeing uses, so totals line up with its screen-time numbers.
 */
object UsageAggregator {
    fun foregroundMs(events: List<FgEvent>, fromMs: Long, toMs: Long): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val openSince = mutableMapOf<String, Long>()
        val seen = mutableSetOf<String>()
        for (e in events.sortedBy { it.timeMs }) {
            val t = e.timeMs.coerceIn(fromMs, toMs)
            if (e.resumed) {
                openSince.putIfAbsent(e.packageName, t)
            } else {
                // A pause without a prior resume means the app was already open at [fromMs].
                val start = openSince.remove(e.packageName) ?: if (e.packageName !in seen) fromMs else null
                if (start != null) totals.merge(e.packageName, t - start, Long::plus)
            }
            seen += e.packageName
        }
        openSince.forEach { (pkg, start) -> totals.merge(pkg, toMs - start, Long::plus) } // still open
        return totals
    }
}
