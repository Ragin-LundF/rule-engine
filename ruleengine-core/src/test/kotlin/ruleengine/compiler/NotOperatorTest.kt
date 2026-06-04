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

class NotOperatorTest {
    @Test
    fun `not operator in rule works`() {
        val txt = """
            rule "not-spam" {
              when
                not purpose contains "spam"

              then
                label "clean"
            }
        """.trimIndent()

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId("purpose") to FieldDefinition(FieldId("purpose"), FieldType.TEXT, normalizers = listOf(NormalizerId("trim"), NormalizerId("lowercase")), operators = setOf(OperatorId("contains"), OperatorId("equals")))
            )
        )

        val validation = Validator.validate(asts, schema)
        assertTrue(validation.isValid)

        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        val engine = RuleEngine(compiledRules = compiled, schema = schema)

        val ctx1 = RuleContext.of("purpose" to "important")
        val prepared1 = ruleengine.evaluator.context.PreparedRuleContext.prepare(ctx = ctx1, schema = schema)
        val res1 = engine.evaluate(prepared1)
        assertEquals(expected = 1, actual = res1.matches.size)

        val ctx2 = RuleContext.of("purpose" to "spammy offer")
        val prepared2 = ruleengine.evaluator.context.PreparedRuleContext.prepare(ctx = ctx2, schema = schema)
        val res2 = engine.evaluate(prepared2)
        assertEquals(expected = 0, actual = res2.matches.size)
    }
}

