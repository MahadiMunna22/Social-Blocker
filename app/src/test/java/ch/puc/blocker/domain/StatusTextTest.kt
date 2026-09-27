package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTextTest {
    private val min = 60_000L

    @Test fun minutesRoundUp() {
        assertEquals(0, StatusText.minutesLeft(0))
        assertEquals(1, StatusText.minutesLeft(1))
        assertEquals(1, StatusText.minutesLeft(min))
        assertEquals(2, StatusText.minutesLeft(min + 1))
        assertEquals(0, StatusText.minutesLeft(-5_000))
    }

    @Test fun headlineNamesBlockedApps() {
        assertEquals("No apps blocked", StatusText.headline(emptyList(), 0))
        val insta = StatusText.Blocked("Instagram", Reason.COOLDOWN, 23 * min)
        val tiktok = StatusText.Blocked("TikTok", Reason.DAILY_CAP, 99 * min)
        assertEquals("Instagram blocked · 23 min", StatusText.headline(listOf(insta), 0))
        assertEquals("Instagram 23 min · TikTok until midnight", StatusText.headline(listOf(insta, tiktok), 0))
    }

    @Test fun inUseSaysWhatItCountsDownTo() {
        assertEquals("Instagram · 7 min until break", StatusText.inUse("Instagram", Remaining(6 * min + 10_000, dailyLimited = false)))
        assertEquals("Instagram · 3 min left today", StatusText.inUse("Instagram", Remaining(3 * min, dailyLimited = true)))
    }
}
