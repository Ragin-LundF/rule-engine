package ruleengine.compiler

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import ruleengine.dsl.parser.Parser
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.RuleContext

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

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId("sepaCode") to FieldDefinition(FieldId("sepaCode"), FieldType.TEXT, normalizers = listOf(NormalizerId("trim"), NormalizerId("uppercase")), operators = setOf(OperatorId("in")))
            )
        )

        val validation = Validator.validate(asts, schema)
        kotlin.test.assertTrue(validation.isValid, "Validation failed: ${validation.diagnostics}")

        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        val engine = RuleEngine(compiledRules = compiled, schema = schema)

        val ctx = RuleContext.of("sepaCode" to "PMNT")
        val prepared = ruleengine.evaluator.context.PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val preparedValue = prepared.get(ruleengine.core.domain.FieldId("sepaCode")) as? ruleengine.evaluator.context.PreparedText
        kotlin.test.assertTrue(preparedValue != null)
        kotlin.test.assertEquals(expected = "PMNT", actual = preparedValue!!.normalized)
        val res = engine.evaluate(prepared)
        assertEquals(expected = 1, actual = res.matches.size)
    }
}

