package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerTest {
    @Test
    fun `parse validate compile and evaluate rule`() {
        val txt = """
            rule "rent-payment" {
              when
                purpose contains "miete"
                and amount >= 500

              then
                label "rent"
            }
        """.trimIndent()

        val parser = Parser(input = txt)
        val asts = parser.parseRules()
        val schema = FieldSchema(
            name = "transaction-v1",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase")),
                    operators = setOf(OperatorId(value = "contains"), OperatorId(value = "equals"))
                ),
                FieldId(value = "amount") to FieldDefinition(
                    id = FieldId(value = "amount"),
                    type = FieldType.DECIMAL,
                    normalizers = emptyList(),
                    operators = setOf(OperatorId(value = "gte"), OperatorId(value = "gt"), OperatorId(value = "equals"))
                )
            )
        )

        val validation = Validator.validate(asts = asts, schema = schema)
        assertTrue(actual = validation.isValid, message = "Validation failed: ${validation.diagnostics}")

        val compiled = Compiler.compileRules(
            asts = asts,
            schema = schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        val engine = RuleEngine(compiledRules = compiled)

        val ctx = RuleContext.of("purpose" to "Miete Januar", "amount" to "850")
        val prepared = ruleengine.evaluator.context.PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val result = engine.evaluate(prepared = prepared)
        assertEquals(expected = 1, actual = result.matches.size)
        assertEquals(expected = "rent-payment", actual = result.matches.first().ruleId)
    }
}

