package ch.puc.blocker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickTaskTest {
    @Test fun plainTitleUsesDefault() = assertEquals(QuickTask.Parsed("Maths homework", 60), QuickTask.parse("Maths homework"))
    @Test fun hours() = assertEquals(QuickTask.Parsed("Essay", 120), QuickTask.parse("Essay 2h"))
    @Test fun decimalHours() = assertEquals(QuickTask.Parsed("Project", 90), QuickTask.parse("Project 1,5h"))
    @Test fun minutes() = assertEquals(QuickTask.Parsed("Read ch. 3", 45), QuickTask.parse("Read ch. 3 45m"))
    @Test fun minSuffix() = assertEquals(QuickTask.Parsed("Vocab", 30), QuickTask.parse("Vocab 30 min"))
    @Test fun blankIsNull() = assertNull(QuickTask.parse("   "))
}
