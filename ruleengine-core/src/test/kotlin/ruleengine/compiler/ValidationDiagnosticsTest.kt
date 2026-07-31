package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationDiagnosticsTest {

    private val schema = FieldSchema(
        name = "test-schema",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "fee") to FieldDefinition(
                id = FieldId(value = "fee"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "name") to FieldDefinition(
                id = FieldId(value = "name"),
                type = FieldType.TEXT
            ),
            FieldId(value = "transactions") to FieldDefinition(
                id = FieldId(value = "transactions"),
                type = FieldType.STRING_SET
            )
        )
    )

    private fun validate(condition: String): ValidationResult {
        val rule = """
            rule "test" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
        val asts = Parser(input = rule).parseRules()
        return Validator.validate(asts = asts, schema = schema)
    }

    // --- unknown function ---

    @Test
    fun `unknown function name produces error with function name in message`() {
        val result = validate(condition = "foobar(transactions) > 0")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("foobar"),
            message = "Expected function name in message, got: ${error.message}"
        )
        assertTrue(
            actual = error.message.contains("supported"),
            message = "Expected supported functions listed, got: ${error.message}"
        )
    }

    // --- wrong arity ---

    @Test
    fun `function with zero arguments produces arity error`() {
        val result = validate(condition = "sum() > 0")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("sum"),
            message = "Expected function name in message, got: ${error.message}"
        )
        assertTrue(
            actual = error.message.contains("one argument") || error.message.contains("0"),
            message = "Expected arity info in message, got: ${error.message}"
        )
    }

    // --- count with text argument ---

    @Test
    fun `count with text field argument produces error mentioning array-like`() {
        val result = validate(condition = "count(name) > 0")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("count"),
            message = "Expected 'count' in message, got: ${error.message}"
        )
        assertTrue(
            actual = error.message.contains("array") || error.message.contains("text"),
            message = "Expected type info in message, got: ${error.message}"
        )
    }

    // --- numeric aggregate with text argument ---

    @Test
    fun `sum with text field argument produces error mentioning array of numbers`() {
        val result = validate(condition = "sum(name) > 0")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("sum"),
            message = "Expected 'sum' in message, got: ${error.message}"
        )
        assertTrue(
            actual = error.message.contains("numbers") || error.message.contains("text"),
            message = "Expected type info in message, got: ${error.message}"
        )
    }

    @Test
    fun `avg with text field argument produces error`() {
        val result = validate(condition = "avg(name) > 0")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(actual = error.message.contains("avg"), message = "Got: ${error.message}")
    }

    // --- arithmetic on non-numeric ---

    @Test
    fun `arithmetic with text literal produces error mentioning operator and text`() {
        val result = validate(condition = """amount + "hello" > 0""")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("+") || error.message.contains("Arithmetic"),
            message = "Expected operator in message, got: ${error.message}"
        )
        assertTrue(
            actual = error.message.contains("text") || error.message.contains("numeric"),
            message = "Expected type info in message, got: ${error.message}"
        )
    }

    // --- incompatible comparison types ---

    @Test
    fun `comparing numeric field to text literal produces incompatible types error`() {
        val result = validate(condition = """amount == "hello"""")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("incompatible") || error.message.contains("type"),
            message = "Expected type mismatch in message, got: ${error.message}"
        )
    }

    // --- invalid operator for text ---

    @Test
    fun `GT operator on text fields produces error mentioning operator`() {
        val result = validate(condition = """name > "alice"""")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("GT") ||
                    error.message.contains(">") ||
                    error.message.contains("not allowed"),
            message = "Expected operator info in message, got: ${error.message}"
        )
    }

    @Test
    fun `LT operator on text fields produces error`() {
        val result = validate(condition = """name < "alice"""")
        assertFalse(actual = result.isValid)
        assertTrue(actual = result.diagnostics.any { it.severity == Severity.ERROR })
    }

    // --- unknown field ---

    @Test
    fun `unknown field in expression produces error with field name`() {
        val result = validate(condition = "unknown_field > 100")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("unknown_field"),
            message = "Expected field name in message, got: ${error.message}"
        )
        assertTrue(
            actual = error.message.contains("Unknown field") || error.message.contains("unknown"),
            message = "Expected 'Unknown field' in message, got: ${error.message}"
        )
    }

    // --- all errors are Severity.ERROR ---

    @Test
    fun `all diagnostics for invalid expression have ERROR severity`() {
        val result = validate(condition = "foobar(transactions) > 0")
        assertTrue(
            actual = result.diagnostics.all { it.severity == Severity.ERROR },
            message = "Expected all diagnostics to be ERROR, got: ${result.diagnostics}"
        )
    }

    // --- valid expressions still pass ---

    @Test
    fun `valid sum expression passes validation`() {
        val result = validate(condition = "sum(transactions.amount) > 100")
        assertTrue(actual = result.isValid, message = "Expected valid: ${result.diagnostics}")
    }

    @Test
    fun `valid count expression passes validation`() {
        val result = validate(condition = "count(transactions) > 0")
        assertTrue(actual = result.isValid, message = "Expected valid: ${result.diagnostics}")
    }

    @Test
    fun `valid filtered sum expression passes validation`() {
        val result = validate(condition = """sum(transactions[amount > 0].amount) > 100""")
        assertTrue(actual = result.isValid, message = "Expected valid: ${result.diagnostics}")
    }

    // --- diagnostic count ---

    @Test
    fun `unknown function produces exactly one error`() {
        val result = validate(condition = "foobar(transactions) > 0")
        val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
        assertEquals(expected = 1, actual = errors.size, message = "Expected exactly 1 error, got: $errors")
    }

    @Test
    fun `wrong arity produces exactly one error`() {
        val result = validate(condition = "sum() > 0")
        val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
        assertEquals(expected = 1, actual = errors.size, message = "Expected exactly 1 error, got: $errors")
    }
}
