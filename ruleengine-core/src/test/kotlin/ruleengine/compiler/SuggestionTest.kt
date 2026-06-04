package ruleengine.compiler

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(
            name = "s",
            fields = mapOf(
                FieldId("purpose") to FieldDefinition(
                    FieldId("purpose"),
                    FieldType.TEXT,
                    normalizers = listOf(NormalizerId("trim")),
                    operators = setOf(OperatorId("contains"))
                )
            )
        )
        val res = Validator.validate(asts, schema)
        assertFalse(res.isValid)
        val diag = res.diagnostics.firstOrNull()
        assertTrue(diag?.suggestion == "purpose")
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

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(
            name = "s",
            fields = mapOf(
                FieldId("purpose") to FieldDefinition(
                    FieldId("purpose"),
                    FieldType.TEXT,
                    normalizers = listOf(NormalizerId("trim")),
                    operators = setOf(OperatorId("contains"), OperatorId("equals"))
                )
            )
        )
        val res = Validator.validate(asts, schema)
        assertFalse(res.isValid)
        val diag = res.diagnostics.firstOrNull { it.message.contains("Operator") }
        assertTrue(diag?.suggestion == "contains")
    }
}

