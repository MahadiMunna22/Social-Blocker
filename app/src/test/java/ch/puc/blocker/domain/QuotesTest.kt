package ch.puc.blocker.domain

import ch.puc.blocker.domain.Quotes.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotesTest {
    @Test fun olderSingleChoiceStillWorks() {
        assertEquals(setOf(Source.QURAN), Quotes.sources(mask = 0, legacy = Source.QURAN.ordinal))
    }

    @Test fun tickedSetsWin_andNeverEmpty() {
        assertEquals(setOf(Source.POPULAR, Source.BIBLE), Quotes.sources(Source.POPULAR.bit or Source.BIBLE.bit, legacy = 1))
        assertEquals(Source.entries.toSet(), Quotes.sources(0b111, legacy = 0))
        assertEquals(setOf(Source.POPULAR), Quotes.sources(mask = 0, legacy = 99))
    }
}
