package ruleengine.compiler

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
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

        val parser = Parser(txt)
        val asts = parser.parseRules()
        val schema = FieldSchema(
            name = "transaction-v1",
            fields = mapOf(
                FieldId("purpose") to FieldDefinition(
                    FieldId("purpose"),
                    FieldType.TEXT,
                    normalizers = listOf(NormalizerId("trim"), NormalizerId("lowercase")),
                    operators = setOf(OperatorId("contains"), OperatorId("equals"))
                ),
                FieldId("amount") to FieldDefinition(
                    FieldId("amount"),
                    FieldType.DECIMAL,
                    normalizers = emptyList(),
                    operators = setOf(OperatorId("gte"), OperatorId("gt"), OperatorId("equals"))
                )
            )
        )

        val validation = Validator.validate(asts, schema)
        assertTrue(validation.isValid, "Validation failed: ${validation.diagnostics}")

        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        val engine = RuleEngine(compiledRules = compiled, schema = schema)

        val ctx = RuleContext.of("purpose" to "Miete Januar", "amount" to "850")
        val prepared = ruleengine.evaluator.context.PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val result = engine.evaluate(prepared)
        assertEquals(expected = 1, actual = result.matches.size)
        assertEquals(expected = "rent-payment", actual = result.matches.first().ruleId)
    }
}

