package ruleengine.evaluator

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.domain.RuleAction
import ruleengine.evaluator.compiled.AndExpression
import ruleengine.evaluator.compiled.ComparisonOperator
import ruleengine.evaluator.compiled.DecimalComparisonExpression
import ruleengine.evaluator.compiled.TextEqualsExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleEngineTest {
    @Test
    fun `programmatic compiled rule matches input`() {
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"), type = FieldType.TEXT,
                    normalizers = listOf(
                        NormalizerId(value = "trim")
                    ),
                    operators = setOf(OperatorId(value = "equals"))
                ),
                FieldId(value = "amount") to FieldDefinition(
                    id = FieldId(value = "amount"), type = FieldType.DECIMAL,
                    normalizers = emptyList(), operators = setOf(OperatorId(value = "gte"))
                )
            )
        )

        val expr = AndExpression(
            children = listOf(
                TextEqualsExpression(field = FieldId(value = "purpose"), expectedNormalized = "miete"),
                DecimalComparisonExpression(
                    field = FieldId(value = "amount"),
                    expected = BigDecimal("500"),
                    op = ComparisonOperator.GTE
                )
            )
        )

        val rule = CompiledRule(
            id = "rent-payment",
            expression = expr,
            actions = listOf(RuleAction(name = "label", arguments = listOf("rent")))
        )

        val engine = RuleEngine(compiledRules = listOf(rule), schema = schema)

        val ctx = RuleContext.of("purpose" to "miete", "amount" to "850")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val result = engine.evaluate(prepared = prepared)
        assertEquals(expected = 1, actual = result.matches.size)
        assertEquals(expected = "rent-payment", actual = result.matches.first().ruleId)
    }
}

