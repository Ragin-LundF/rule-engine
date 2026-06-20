package ruleengine.compiler

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComparisonExpressionValidatorTest {

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

    @Test
    fun `valid numeric comparison passes validation`() {
        val result = validate("amount > 100")
        assertTrue(actual = result.isValid, message = "Expected valid: ${result.diagnostics}")
    }

    @Test
    fun `valid arithmetic comparison passes validation`() {
        val result = validate("amount + fee * 2 <= 100")
        assertTrue(actual = result.isValid, message = "Expected valid: ${result.diagnostics}")
    }

    @Test
    fun `arithmetic with text operand fails validation`() {
        val result = validate("""amount + "text" > 100""")
        assertFalse(actual = result.isValid, message = "Expected invalid")
        assertTrue(
            actual = result.diagnostics.any { it.message.contains("numeric") },
            message = "Expected numeric error message, got: ${result.diagnostics}"
        )
    }

    @Test
    fun `text field with numeric comparison operator fails validation`() {
        val result = validate("name > 100")
        assertFalse(actual = result.isValid, message = "Expected invalid")
    }

    @Test
    fun `unknown field in modern expression fails validation`() {
        val result = validate("unknown_field > 100")
        assertFalse(actual = result.isValid, message = "Expected invalid")
        assertTrue(
            actual = result.diagnostics.any { it.message.contains("Unknown field") },
            message = "Expected unknown field error, got: ${result.diagnostics}"
        )
    }

    @Test
    fun `text equality comparison passes validation`() {
        val result = validate("""name == "alice"""")
        assertTrue(actual = result.isValid, message = "Expected valid: ${result.diagnostics}")
    }

    @Test
    fun `text inequality comparison passes validation`() {
        val result = validate("""name != "alice"""")
        assertTrue(actual = result.isValid, message = "Expected valid: ${result.diagnostics}")
    }

    @Test
    fun `text field with GT operator fails validation`() {
        val result = validate("""name > "alice"""")
        assertFalse(actual = result.isValid, message = "Expected invalid")
        assertTrue(
            actual = result.diagnostics.any { it.message.contains("text") || it.message.contains("not allowed") },
            message = "Expected operator error for text field, got: ${result.diagnostics}"
        )
    }
}
