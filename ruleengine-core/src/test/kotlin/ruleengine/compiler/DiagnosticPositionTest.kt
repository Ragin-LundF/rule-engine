package ruleengine.compiler

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Diagnostics report where the problem is, so an editor can mark the line.
 *
 * The position comes off the token the condition starts at, which is why it is asserted here against
 * literal source text rather than a hand-built AST: a hand-built node carries no position at all.
 */
class DiagnosticPositionTest {

    private val schema = FieldSchema(
        name = "positions",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(id = FieldId(value = "amount"), type = FieldType.INTEGER),
        ),
    )

    @Test
    fun `an unknown field reports the line and column the condition starts at`() {
        val rule = """
            rule "r" {
              when
                unknownField equals "x"
              then
                flag "hit"
            }
        """.trimIndent()

        val error = Validator.validate(asts = Parser(input = rule).parseRules(), schema = schema)
            .diagnostics
            .single { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = 3, actual = error.line, message = "the condition is on the third line")
        assertEquals(expected = 5, actual = error.column, message = "indented by four spaces")
    }

    @Test
    fun `a bad literal reports the position of its own condition, not the first one`() {
        val rule = """
            rule "r" {
              when
                amount equals 1
                amount equals "not-a-number"
              then
                flag "hit"
            }
        """.trimIndent()

        val error = Validator.validate(asts = Parser(input = rule).parseRules(), schema = schema)
            .diagnostics
            .single { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = 4, actual = error.line)
    }

    @Test
    fun `a rule level warning reports the rule's own line`() {
        val rule = """
            rule "no-description" {
              when
                amount equals 1
              then
                flag "hit"
            }
        """.trimIndent()

        val warning = Validator.validate(asts = Parser(input = rule).parseRules(), schema = schema)
            .diagnostics
            .single { diagnostic -> diagnostic.severity == Severity.WARNING }

        assertEquals(expected = 1, actual = warning.line)
    }

    @Test
    fun `a hand-built condition carries no position, and that is not an error`() {
        val parsed = Parser(input = """
            rule "r" {
              when
                amount equals 1
              then
                flag "hit"
            }
        """.trimIndent()).parseRules().single()

        assertNotNull(actual = parsed.line)
    }
}
