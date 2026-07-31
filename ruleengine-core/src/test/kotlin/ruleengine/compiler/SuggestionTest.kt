package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SuggestionTest {
    @Test
    fun `suggest field name for typo`() {
        val txt = """
            rule "r1" {
              when
                purpse contains "x"
              then
                label "a"
            }
        """.trimIndent()

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "s",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim")),
                    operators = setOf(OperatorId(value = "contains"))
                )
            )
        )
        val res = Validator.validate(asts = asts, schema = schema)
        assertFalse(actual = res.isValid)
        val diag = res.diagnostics.firstOrNull()
        assertEquals(expected = diag?.suggestion, actual = "purpose")
    }

    @Test
    fun `suggest operator name for typo`() {
        val txt = """
            rule "r2" {
              when
                purpose contans "x"
              then
                label "a"
            }
        """.trimIndent()

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "s",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim")),
                    operators = setOf(OperatorId(value = "contains"), OperatorId(value = "equals"))
                )
            )
        )
        val res = Validator.validate(asts = asts, schema = schema)
        assertFalse(actual = res.isValid)
        val diag = res.diagnostics.firstOrNull { it.message.contains("Operator") }
        assertEquals(expected = diag?.suggestion, actual = "contains")
    }
}

