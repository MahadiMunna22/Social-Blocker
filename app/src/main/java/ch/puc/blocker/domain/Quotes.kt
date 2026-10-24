package ch.puc.blocker.domain

import android.content.Context
import org.json.JSONObject

/**
 * Quotes for the block and pause screens, from assets/quotes.json (40–50 per set, incl. consequences of lust and wasted time).
 * Quran: Clear Quran (Talal Itani). Bible: World English Bible. Popular: named only where the attribution is sure.
 * The setting stores the [Source] ordinal.
 */
object Quotes {
    /** [keys]: the quotes.json sets it draws from. Ticking Quran brings the hadith along. */
    enum class Source(val label: String, val keys: List<String>) {
        POPULAR("Popular", listOf("popular")), QURAN("Quran & Hadith", listOf("quran", "hadith")), BIBLE("Bible", listOf("bible"));

        val bit get() = 1 shl ordinal
    }

    data class Quote(val text: String, val source: String)

    @Volatile private var all: Map<String, List<Quote>>? = null

    private fun load(ctx: Context): Map<String, List<Quote>> = all ?: run {
        val json = JSONObject(ctx.assets.open("quotes.json").bufferedReader().use { it.readText() })
        json.keys().asSequence().associateWith { key ->
            val arr = json.getJSONArray(key)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Quote(o.getString("t"), o.getString("s")) } }
        }.also { all = it }
    }

    /** [n] different quotes drawn from all [sources] together, in random order. */
    fun pick(ctx: Context, sources: Set<Source>, n: Int = 5): List<Quote> =
        sources.flatMap { s -> s.keys.flatMap { load(ctx)[it].orEmpty() } }.shuffled().take(n)

    /** Ticked sets from the settings; never empty. [legacy] is the older single-choice ordinal. */
    fun sources(mask: Int, legacy: Int): Set<Source> {
        val bits = if (mask != 0) mask else 1 shl legacy
        return Source.entries.filter { bits and it.bit != 0 }.toSet().ifEmpty { setOf(Source.POPULAR) }
    }
}
