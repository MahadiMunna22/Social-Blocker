
package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayLinkTest {
    @Test fun parsesLinksAndPackageNames() {
        assertEquals("com.zhiliaoapp.musically", PlayLink.packageName("https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically&hl=en"))
        assertEquals("com.bereal.ft", PlayLink.packageName("  com.bereal.ft "))
        assertNull(PlayLink.packageName("tiktok"))
        assertNull(PlayLink.packageName("Tik Tok.app"))
    }
}
