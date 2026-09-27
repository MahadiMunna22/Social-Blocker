package ch.puc.blocker.domain

/** Wording for the ongoing notification. Pure, so it can be unit tested. */
object StatusText {
    data class Blocked(val label: String, val reason: Reason, val untilMs: Long)

    /** Whole minutes, rounded up so a running block never reads "0 min". */
    fun minutesLeft(ms: Long): Long = ((ms + 59_999) / 60_000).coerceAtLeast(0)

    fun timeLeft(b: Blocked, now: Long): String =
        if (b.reason == Reason.DAILY_CAP) "until midnight" else "${minutesLeft(b.untilMs - now)} min"

    /** "No apps blocked", "Instagram blocked · 23 min" or "Instagram 23 min · TikTok until midnight". */
    fun headline(blocked: List<Blocked>, now: Long): String = when (blocked.size) {
        0 -> "No apps blocked"
        1 -> "${blocked[0].label} blocked · ${timeLeft(blocked[0], now)}"
        else -> blocked.joinToString(" · ") { "${it.label} ${timeLeft(it, now)}" }
    }

    /** One line per blocked app for the expanded notification. */
    fun blockedLine(b: Blocked, now: Long): String = "⛔ ${b.label} · ${timeLeft(b, now)}"

    /** Status of the app in use: "Instagram · 7 min until break". */
    fun inUse(label: String, r: Remaining): String =
        "$label · ${minutesLeft(r.ms)} min ${if (r.dailyLimited) "left today" else "until break"}"

    fun reward(label: String, msLeft: Long): String = "$label · reward, ${minutesLeft(msLeft)} min left"
}
