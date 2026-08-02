package ui.builder

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.DslFunctions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `every offered operator name is canonical and known to the engine`() {
        OperatorOptions.ALL.forEach { operator ->
            assertTrue(
                actual = OperatorUtils.isKnownOperator(op = operator),
                message = "The engine does not know operator '$operator'",
            )
            assertEquals(
                expected = operator,
                actual = OperatorUtils.normalizeOperator(op = operator),
                message = "'$operator' is not the canonical spelling",
            )
        }
    }

    @Test
    fun `aggregate function names are offered in the engine's declaration order`() {
        assertEquals(
            expected = AggregateFunctionName.entries.filter { it.isAggregate }.map { it.dslName },
            actual = OperatorOptions.AGGREGATE_FUNCTIONS,
        )
    }

    /**
     * The picker is a subset on purpose. Highlighting and completion must still recognise every
     * function the parser accepts, so the wider list has to stay reachable and stay a superset.
     */
    @Test
    fun `all function names cover the aggregates and every other engine function`() {
        assertTrue(
            actual = OperatorOptions.ALL_FUNCTIONS.containsAll(AggregateFunctionName.entries.map { it.dslName }),
            message = "every registered function must be recognised by the editor",
        )
        assertTrue(
            actual = OperatorOptions.ALL_FUNCTIONS.containsAll(DslFunctions.SLICE_NAMES),
            message = "the slice functions are parser sugar, so only this list can carry them",
        )
        assertTrue(
            actual = OperatorOptions.ALL_FUNCTIONS.containsAll(OperatorOptions.AGGREGATE_FUNCTIONS),
            message = "the aggregate picker must offer nothing the editor cannot recognise",
        )
    }

    @Test
    fun `a non-aggregate function is not offered in the aggregate picker`() {
        assertFalse(
            actual = OperatorOptions.AGGREGATE_FUNCTIONS.contains(AggregateFunctionName.DAYS_BETWEEN.dslName),
            message = "daysBetween takes two dates, not a collection path",
        )
        assertTrue(
            actual = OperatorOptions.ALL_FUNCTIONS.contains(AggregateFunctionName.DAYS_BETWEEN.dslName),
            message = "daysBetween is still a function the editor must recognise",
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
