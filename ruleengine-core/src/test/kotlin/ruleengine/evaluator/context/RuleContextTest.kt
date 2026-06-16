package ruleengine.evaluator.context

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import ruleengine.core.domain.FieldId

class RuleContextTest {

    @Test
    fun `test nested field access`() {
        val nested = mapOf("b" to "c")
        val context = RuleContext.of("a" to nested)
        val result = context.get(FieldId("a.b"))
        assertEquals("c", result)
    }

    @Test
    fun `test single level access`() {
        val context = RuleContext.of("a" to "c")
        val result = context.get(FieldId("a"))
        assertEquals("c", result)
    }

    @Test
    fun `test non-map mid-node returns null`() {
        val nested = mapOf("a" to "not-a-map")
        val context = RuleContext.of("a" to nested)
        val result = context.get(FieldId("a.b"))
        assertNull(result)
    }

    @Test
    fun `test empty segment in path returns null`() {
        val nested = mapOf("a" to mapOf("b" to "c"))
        val context = RuleContext.of("a" to nested)
        val result = context.get(FieldId("a..b"))
        assertNull(result)
    }
}
