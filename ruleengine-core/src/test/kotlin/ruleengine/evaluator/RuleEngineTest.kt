package ruleengine.evaluator

import kotlin.test.Test
import kotlin.test.assertEquals
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.domain.RuleAction
import ruleengine.evaluator.compiled.DecimalComparisonExpression
import ruleengine.evaluator.compiled.TextEqualsExpression
import ruleengine.evaluator.compiled.ComparisonOperator
import ruleengine.evaluator.compiled.AndExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.math.BigDecimal

class RuleEngineTest {
    @Test
    fun `programmatic compiled rule matches input`() {
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId("purpose") to FieldDefinition(
                    id = FieldId("purpose"), type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId("trim")), operators = setOf(OperatorId("equals"))
                ),
                FieldId("amount") to FieldDefinition(
                    id = FieldId("amount"), type = FieldType.DECIMAL,
                    normalizers = emptyList(), operators = setOf(OperatorId("gte"))
                )
            )
        )

        val expr = AndExpression(listOf(
            TextEqualsExpression(field = FieldId("purpose"), expectedNormalized = "miete"),
            DecimalComparisonExpression(field = FieldId("amount"), expected = BigDecimal("500"), op = ComparisonOperator.GTE)
        ))

        val rule = CompiledRule(id = "rent-payment", expression = expr, actions = listOf(RuleAction(name = "label", arguments = listOf("rent"))))

        val engine = RuleEngine(compiledRules = listOf(rule), schema = schema)

        val ctx = RuleContext.of("purpose" to "miete", "amount" to "850")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val result = engine.evaluate(prepared)
        assertEquals(expected = 1, actual = result.matches.size)
        assertEquals(expected = "rent-payment", actual = result.matches.first().ruleId)
    }
}

