package ch.puc.blocker.domain

/** Parses a one-line task typed into a notification, e.g. "Essay 2h", "Read ch. 3 45m", "Maths". */
object QuickTask {
    data class Parsed(val title: String, val estimateMin: Int)

    private val duration = Regex("""\s+(\d+(?:[.,]\d+)?)\s*(h|m|min)$""", RegexOption.IGNORE_CASE)

    fun parse(input: String, defaultMin: Int = 60): Parsed? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val m = duration.find(text) ?: return Parsed(text, defaultMin)
        val value = m.groupValues[1].replace(',', '.').toDouble()
        val minutes = if (m.groupValues[2].lowercase() == "h") value * 60 else value
        val title = text.substring(0, m.range.first).trim()
        return Parsed(title.ifEmpty { text }, minutes.toInt().coerceIn(5, 480))
    }
}
