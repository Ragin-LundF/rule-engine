package ui.builder

import ruleengine.evaluator.compiled.AggregateFunctionName
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
    fun `integer does not offer in, which the engine allows on text only`() {
        assertTrue("in" !in OperatorOptions.INTEGER)
        assertTrue("in" !in OperatorOptions.DECIMAL)
        assertTrue("in" in OperatorOptions.TEXT)
    }

    @Test
    fun `aggregate function list matches the engine enum`() {
        assertEquals(
            expected = AggregateFunctionName.entries.map { it.name.lowercase() }.sorted(),
            actual = OperatorOptions.AGGREGATE_FUNCTIONS.sorted(),
        )
    }

    @Test
    fun `forField gives date_time the same operators as date`() {
        assertEquals(OperatorOptions.DATE, OperatorOptions.forField(fieldType = "date_time"))
        assertEquals(OperatorOptions.DATE, OperatorOptions.forField(fieldType = "date"))
    }

    @Test
    fun `forField restricts date_time to the schema operators`() {
        val ops = OperatorOptions.forField(fieldType = "date_time", schemaOperators = listOf("gt", "between"))
        assertTrue(">" in ops)
        assertTrue("between" in ops)
        assertTrue("equals" !in ops)
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
