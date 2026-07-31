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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase")),
                    operators = setOf(OperatorId(value = "contains"), OperatorId(value = "equals"))
                )
            )
        )

        val validation = Validator.validate(asts, schema)
        assertTrue(actual = validation.isValid)

        val compiled = Compiler.compileRules(
            asts = asts,
            schema = schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        val engine = RuleEngine(compiledRules = compiled)

        val ctx1 = RuleContext.of("purpose" to "important")
        val prepared1 = PreparedRuleContext.prepare(ctx = ctx1, schema = schema)
        val res1 = engine.evaluate(prepared = prepared1)
        assertEquals(expected = 1, actual = res1.matches.size)

        val ctx2 = RuleContext.of("purpose" to "spammy offer")
        val prepared2 = PreparedRuleContext.prepare(ctx = ctx2, schema = schema)
        val res2 = engine.evaluate(prepared = prepared2)
        assertEquals(expected = 0, actual = res2.matches.size)
    }
}

