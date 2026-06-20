package ruleengine.evaluator

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.trace.dto.DecisionTree
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraceTest {
    @Test
    fun `evaluation produces decision trace with all evaluated rules`() {
        val engine = buildEngine()
        val result = engine.evaluate(prepared = preparedContext(), includeTrace = true)

        assertTraceContainsAllRules(result = result)
    }

    private fun buildEngine(): RuleEngine {
        val asts = Parser(input = traceRules()).parseRules()
        val schema = traceSchema()

        val validation = Validator.validate(asts = asts, schema = schema)
        assertTrue(actual = validation.isValid)

        val compiled = Compiler.compileRules(
            asts = asts,
            schema = schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        return RuleEngine(compiledRules = compiled, schema = schema)
    }

    private fun preparedContext(): ruleengine.evaluator.context.PreparedRuleContext {
        return ruleengine.evaluator.context.PreparedRuleContext.prepare(
            ctx = RuleContext.of(
                "purpose" to "Miete Januar",
                "amount" to "850"
            ),
            schema = traceSchema()
        )
    }

    private fun assertTraceContainsAllRules(result: ruleengine.core.domain.EvaluationResult) {
        assertNotNull(actual = result.trace)
        val tree = result.trace as? DecisionTree
        assertNotNull(actual = tree)
        assertTrue(actual = tree.matchedRules.contains("rent-payment"))

        val root = tree.root
        assertNotNull(actual = root)
        assertTrue(actual = root.children.size == 2)

        val tracedRuleIds = root.children.mapNotNull { it.ruleId }
        assertTrue(actual = tracedRuleIds.contains("rent-payment"))
        assertTrue(actual = tracedRuleIds.contains("vip-payment"))

        val nonMatchingRule = root.children.first { it.ruleId == "vip-payment" }
        assertTrue(actual = nonMatchingRule.result.not())
    }

    private fun traceSchema(): FieldSchema {
        return FieldSchema(
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
                    operators = setOf(
                        OperatorId(value = "gte"),
                        OperatorId(value = "gt"),
                        OperatorId(value = "equals")
                    )
                )
            )
        )
    }

    private fun traceRules(): String {
        return """
            rule "rent-payment" {
              when
                purpose contains "miete"
                and amount >= 500

              then
                label "rent"
            }

            rule "vip-payment" {
              when
                purpose contains "vip"

              then
                label "vip"
            }
        """.trimIndent()
    }
}

