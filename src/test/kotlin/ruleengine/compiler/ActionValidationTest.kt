package ruleengine.compiler

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import ruleengine.dsl.parser.Parser
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.domain.DefaultActionSchema

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

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(name = "t", fields = mapOf(FieldId("purpose") to FieldDefinition(FieldId("purpose"), FieldType.TEXT, normalizers = listOf(NormalizerId("trim")), operators = setOf(OperatorId("contains")))))

        val result = Validator.validate(asts = asts, schema = schema, actions = DefaultActionSchema.basic)
        assertTrue(result.isValid, "Expected actions to validate: ${result.diagnostics}")
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

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(name = "t", fields = mapOf(FieldId("purpose") to FieldDefinition(FieldId("purpose"), FieldType.TEXT, normalizers = listOf(NormalizerId("trim")), operators = setOf(OperatorId("contains")))))

        val result = Validator.validate(asts = asts, schema = schema, actions = DefaultActionSchema.basic)
        assertFalse(result.isValid)
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

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(name = "t", fields = mapOf(FieldId("purpose") to FieldDefinition(FieldId("purpose"), FieldType.TEXT, normalizers = listOf(NormalizerId("trim")), operators = setOf(OperatorId("contains")))))

        val result = Validator.validate(asts = asts, schema = schema, actions = DefaultActionSchema.basic)
        assertFalse(result.isValid)
    }
}

