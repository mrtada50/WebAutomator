package com.webautomator.app.engine

import com.webautomator.app.data.ConditionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonTest {
    @Test fun firstRunHasNoComparison() {
        val c = ValueComparator.compare(null, mapOf("price" to "125"))
        assertTrue(c.first)
        assertFalse(c.changed)
    }

    @Test fun numericChange() {
        val c = ValueComparator.compare(mapOf("price" to "125"), mapOf("price" to "110"))
        assertTrue(c.changed)
        assertTrue(c.summary.contains("125 → 110"))
        assertTrue(c.summary.contains("-15"))
    }

    @Test fun noChange() {
        val c = ValueComparator.compare(mapOf("a" to "x"), mapOf("a" to "x"))
        assertFalse(c.changed)
        assertEquals("", c.summary)
    }

    @Test fun listAddedAndRemoved() {
        val c = ValueComparator.compare(mapOf("k" to "a\nb"), mapOf("k" to "b\nc"))
        assertTrue(c.summary.contains("+ c"))
        assertTrue(c.summary.contains("- a"))
    }

    @Test fun removedKey() {
        val c = ValueComparator.compare(mapOf("k" to "1", "j" to "2"), mapOf("k" to "1"))
        assertTrue(c.changed)
        assertTrue(c.summary.contains("- j"))
    }

    @Test fun conditions() {
        assertTrue(Condition.evaluate(ConditionType.GREATER, "110", "100", null))
        assertFalse(Condition.evaluate(ConditionType.LESS, "110", "100", null))
        assertTrue(Condition.evaluate(ConditionType.EQUAL, "125", "125.0", null))
        assertTrue(Condition.evaluate(ConditionType.CONTAINS, "In Stock", "stock", null))
        assertTrue(Condition.evaluate(ConditionType.NOT_CONTAINS, "In Stock", "sold", null))
        assertTrue(Condition.evaluate(ConditionType.CHANGED, "b", "", "a"))
        assertFalse(Condition.evaluate(ConditionType.CHANGED, "b", "", null))
        assertFalse(Condition.evaluate(ConditionType.GREATER, null, "1", null))
    }
}
