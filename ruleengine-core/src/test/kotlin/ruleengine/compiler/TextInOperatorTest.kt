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
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.context.dto.PreparedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextInOperatorTest {
    @Test
    fun `text in operator with list matches`() {
        val txt = """
            rule "card-payment" {
              when
                sepaCode in ["PMNT", "CCRD"]

              then
                label "card"
            }
        """.trimIndent()

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "sepaCode") to FieldDefinition(
                    id = FieldId(value = "sepaCode"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "uppercase")),
                    operators = setOf(OperatorId(value = "in"))
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

        val ctx = RuleContext.of("sepaCode" to "PMNT")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val preparedValue = prepared.get(FieldId(value = "sepaCode")) as? PreparedText
        assertTrue(actual = preparedValue != null)
        assertEquals(expected = "PMNT", actual = preparedValue.normalized)
        val res = engine.evaluate(prepared = prepared)
        assertEquals(expected = 1, actual = res.matches.size)
    }
}

