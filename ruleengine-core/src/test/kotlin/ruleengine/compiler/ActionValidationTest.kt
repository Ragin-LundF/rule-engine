package ruleengine.compiler

import ruleengine.core.domain.DefaultActionSchema
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionValidationTest {
    @Test
    fun `valid actions pass validation`() {
        val txt = """
            rule "r" {
              when
                purpose contains "miete"
              then
                label "rent"
                score 10
            }
        """.trimIndent()

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "t",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim")),
                    operators = setOf(OperatorId(value = "contains"))
                )
            )
        )

        val result = Validator.validate(asts = asts, schema = schema, actions = DefaultActionSchema.basic)
        assertTrue(actual = result.isValid, message = "Expected actions to validate: ${result.diagnostics}")
    }

    @Test
    fun `unknown action fails validation`() {
        val txt = """
            rule "r" {
              when
                purpose contains "miete"
              then
                unknownAction "x"
            }
        """.trimIndent()

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "t",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId("purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim")),
                    operators = setOf(OperatorId(value = "contains"))
                )
            )
        )

        val result = Validator.validate(asts = asts, schema = schema, actions = DefaultActionSchema.basic)
        assertFalse(actual = result.isValid)
    }

    @Test
    fun `wrong action arg type fails validation`() {
        val txt = """
            rule "r" {
              when
                purpose contains "miete"
              then
                score "high"
            }
        """.trimIndent()

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "t",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId("purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim")),
                    operators = setOf(OperatorId(value = "contains"))
                )
            )
        )

        val result = Validator.validate(asts = asts, schema = schema, actions = DefaultActionSchema.basic)
        assertFalse(actual = result.isValid)
    }
}

