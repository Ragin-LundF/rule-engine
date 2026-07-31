package ruleengine.compiler.operators

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperatorUtilsTest {

    @Test
    fun `symbolic operators normalize to their named form`() {
        assertEquals(expected = "equals", actual = OperatorUtils.normalizeOperator(op = "=="))
        assertEquals(expected = "gte", actual = OperatorUtils.normalizeOperator(op = ">="))
        assertEquals(expected = "lt", actual = OperatorUtils.normalizeOperator(op = "<"))
    }

    @Test
    fun `operator names are matched case-insensitively`() {
        assertEquals(expected = "startsWith", actual = OperatorUtils.normalizeOperator(op = "StartsWith"))
        assertEquals(expected = "containsAny", actual = OperatorUtils.normalizeOperator(op = "CONTAINSANY"))
    }

    /** Earlier versions of the visual schema editor wrote these into `operators:` lists. */
    @Test
    fun `legacy snake_case spellings normalize to the canonical name`() {
        assertEquals(expected = "startsWith", actual = OperatorUtils.normalizeOperator(op = "starts_with"))
        assertEquals(expected = "endsWith", actual = OperatorUtils.normalizeOperator(op = "ends_with"))
    }

    @Test
    fun `an unknown operator is returned unchanged`() {
        assertEquals(expected = "greaterThan", actual = OperatorUtils.normalizeOperator(op = "greaterThan"))
    }

    @Test
    fun `isKnownOperator accepts every spelling the engine can compile`() {
        listOf("equals", "==", "=", "eq", "gt", ">", "gte", ">=", "lt", "<", "lte", "<=", "between")
            .forEach { assertTrue(actual = OperatorUtils.isKnownOperator(op = it), message = it) }
        listOf("contains", "startsWith", "starts_with", "endsWith", "in", "containsAny", "containsAll", "regex")
            .forEach { assertTrue(actual = OperatorUtils.isKnownOperator(op = it), message = it) }
        // symbolic inequality is routed through the expression engine rather than a field operator
        assertTrue(actual = OperatorUtils.isKnownOperator(op = "!="))
    }

    @Test
    fun `isKnownOperator rejects names the engine has no implementation for`() {
        listOf("greaterThan", "lessThan", "not_contains", "isEmpty", "isNotEmpty", "nope")
            .forEach { assertFalse(actual = OperatorUtils.isKnownOperator(op = it), message = it) }
    }
}
