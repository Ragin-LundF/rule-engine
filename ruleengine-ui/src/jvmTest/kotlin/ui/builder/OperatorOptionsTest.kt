package ui.builder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatorOptionsTest {

    @Test
    fun `forField with no schema operators returns full defaults for integer`() {
        val ops = OperatorOptions.forField(fieldType = "integer")
        assertEquals(OperatorOptions.INTEGER, ops)
    }

    @Test
    fun `forField normalizes schema gt gte lt lte to symbols for integer`() {
        val schemaOps = listOf("equals", "gt", "gte", "lt", "lte", "between")
        val ops = OperatorOptions.forField(fieldType = "integer", schemaOperators = schemaOps)
        assertTrue("equals" in ops, "expected equals")
        assertTrue(">" in ops, "expected >")
        assertTrue(">=" in ops, "expected >=")
        assertTrue("<" in ops, "expected <")
        assertTrue("<=" in ops, "expected <=")
        assertTrue("between" in ops, "expected between")
    }

    @Test
    fun `forField with schema gt gte does not include operators not in schema`() {
        val schemaOps = listOf("gt", "gte")
        val ops = OperatorOptions.forField(fieldType = "integer", schemaOperators = schemaOps)
        assertTrue(">" in ops)
        assertTrue(">=" in ops)
        assertTrue("equals" !in ops)
        assertTrue("between" !in ops)
    }

    @Test
    fun `forField falls back to schemaOperators when intersection is empty`() {
        val schemaOps = listOf("custom_op")
        val ops = OperatorOptions.forField(fieldType = "integer", schemaOperators = schemaOps)
        assertEquals(schemaOps, ops)
    }

    @Test
    fun `forField normalizes ne and neq to != symbol`() {
        val schemaOps = listOf("ne", "neq", "not_equals")
        val ops = OperatorOptions.forField(fieldType = "integer", schemaOperators = schemaOps)
        assertTrue("!=" in ops)
    }

    @Test
    fun `forField with empty schema operators returns decimal defaults`() {
        val ops = OperatorOptions.forField(fieldType = "decimal")
        assertEquals(OperatorOptions.DECIMAL, ops)
    }

    @Test
    fun `forField normalizes schema operators for decimal type`() {
        val schemaOps = listOf("equals", "gt", "lt", "between")
        val ops = OperatorOptions.forField(fieldType = "decimal", schemaOperators = schemaOps)
        assertTrue("equals" in ops)
        assertTrue(">" in ops)
        assertTrue("<" in ops)
        assertTrue("between" in ops)
        assertTrue(">=" !in ops)
    }
}
