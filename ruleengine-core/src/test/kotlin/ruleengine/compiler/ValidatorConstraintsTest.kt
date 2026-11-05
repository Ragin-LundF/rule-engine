package ruleengine.compiler

import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatorConstraintsTest {
    private val schemaWithoutExplicitOperators = FieldSchema(
        name = "numeric-test",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "count") to FieldDefinition(
                id = FieldId(value = "count"),
                type = FieldType.INTEGER
            )
        )
    )

    @Test
    fun `validator rejects malformed numeric literals and between bounds`() {
        val asts = Parser(
            input = """
                rule "bad-integer-literal" {
                  when count gt 1.2
                  then label "y"
                }

                rule "bad-integer-between" {
                  when count between 1 2.2
                  then label "w"
                }
            """.trimIndent()
        ).parseRules()

        val result = Validator.validate(asts = asts, schema = schemaWithoutExplicitOperators)

        assertFalse(actual = result.isValid)
        assertTrue(actual = result.diagnostics.any { it.message.contains(other = "Invalid integer literal: 1.2") })
        assertTrue(actual = result.diagnostics.any { it.message.contains(other = "Invalid upper bound: 2.2") })
    }

    /** A number has at most one dot, so `1.2.3` no longer reaches the validator: it is a syntax error. */
    @Test
    fun `a number with two dots is a parse error in a condition and in a between bound`() {
        listOf("amount gt 1.2.3", "amount between 1.2.3 5").forEach { condition ->
            val error = assertFailsWith<ParseException> {
                Parser(input = "rule \"bad\" {\n  when $condition\n  then label \"x\"\n}").parseRules()
            }
            assertEquals(expected = 2, actual = error.line, message = condition)
        }
    }

    @Test
    fun `validator accepts supported operator aliases when schema omits explicit operators`() {
        val asts = Parser(
            input = """
                rule "decimal-alias" {
                  when amount >= 100
                  then label "x"
                }

                rule "integer-alias" {
                  when count == 3
                  then label "y"
                }
            """.trimIndent()
        ).parseRules()

        val result = Validator.validate(asts = asts, schema = schemaWithoutExplicitOperators)

        assertTrue(actual = result.isValid, message = "Expected aliases to validate: ${result.diagnostics}")
    }

    @Test
    fun `validator rejects unsupported operator when schema omits explicit operators`() {
        val asts = Parser(
            input = """
                rule "bad-operator" {
                  when amount contains 10
                  then label "x"
                }
            """.trimIndent()
        ).parseRules()

        val result = Validator.validate(asts = asts, schema = schemaWithoutExplicitOperators)

        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any {
                it.message.contains(other = "Operator 'contains' is not allowed for field 'amount'")
            },
            message = "Expected unsupported operator diagnostic, got: ${result.diagnostics}"
        )
    }

    /**
     * A schema written by an older visual editor declares `starts_with`. That has to allow
     * `startsWith`, otherwise the declaration silently restricts the field to a name no rule can use.
     */
    @Test
    fun `validator accepts a condition whose operator is declared in a legacy snake_case spelling`() {
        val schema = FieldSchema(
            name = "legacy-operator-names",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    operators = setOf(OperatorId(value = "equals"), OperatorId(value = "starts_with")),
                )
            )
        )
        val asts = Parser(
            input = """
                rule "legacy-operator" {
                  when purpose startsWith "DE"
                  then label "x"
                }
            """.trimIndent()
        ).parseRules()

        val result = Validator.validate(asts = asts, schema = schema)

        assertTrue(actual = result.isValid, message = "Expected validation to pass, got: ${result.diagnostics}")
    }
}

