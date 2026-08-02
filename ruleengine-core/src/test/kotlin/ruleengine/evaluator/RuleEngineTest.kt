package ruleengine.evaluator

import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.evaluator.compiled.CompiledActionArgument
import ruleengine.evaluator.compiled.logic.AndExpression
import ruleengine.evaluator.compiled.numeric.ComparisonOperator
import ruleengine.evaluator.compiled.numeric.DecimalComparisonExpression
import ruleengine.evaluator.compiled.text.TextEqualsExpression
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
                    normalizers = listOf(NormalizerId(value = "trim")),
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
            actions = listOf(
                CompiledAction(
                    name = "label",
                    arguments = listOf(CompiledActionArgument.Static(value = "rent"))
                )
            )
        )

        val engine = RuleEngine(compiledRules = listOf(rule))

        val ctx = RuleContext.of("purpose" to "miete", "amount" to "850")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val result = engine.evaluate(prepared = prepared)
        assertEquals(expected = 1, actual = result.matches.size)
        assertEquals(expected = "rent-payment", actual = result.matches.first().ruleId)
    }

    private fun purposeEquals(): TextEqualsExpression {
        return TextEqualsExpression(field = FieldId(value = "purpose"), expectedNormalized = "coffee")
    }

    private fun labelRule(id: String, label: String): CompiledRule {
        return CompiledRule(
            id = id,
            expression = purposeEquals(),
            actions = listOf(
                CompiledAction(name = "label", arguments = listOf(CompiledActionArgument.Static(value = label)))
            )
        )
    }

    private fun engineFor(rules: List<CompiledRule>): RuleEngine {
        return RuleEngine(compiledRules = rules)
    }

    @Test
    fun `matches are returned in declaration (list) order`() {
        // Ids are deliberately not alphabetical to prove order follows the list, not id sorting.
        val ids = listOf("gamma", "alpha", "beta")
        val rules = ids.map { labelRule(id = it, label = it) }

        val result = engineFor(rules = rules).evaluate(prepared = preparedPurpose())

        assertEquals(expected = ids, actual = result.matches.map { it.ruleId })
    }

    private fun purposeSchema(): FieldSchema {
        return FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim"))
                )
            )
        )
    }

    private fun preparedPurpose(): PreparedRuleContext {
        val ctx = RuleContext.of("purpose" to "coffee")
        return PreparedRuleContext.prepare(ctx = ctx, schema = purposeSchema())
    }
}
