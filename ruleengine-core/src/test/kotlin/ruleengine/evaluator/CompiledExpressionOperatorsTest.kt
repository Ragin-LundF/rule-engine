package ruleengine.evaluator

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.evaluator.compiled.IntegerComparisonExpression
import ruleengine.evaluator.compiled.IntegerComparisonOperator
import ruleengine.evaluator.compiled.StringSetContainsAllExpression
import ruleengine.evaluator.compiled.StringSetContainsAnyExpression
import ruleengine.evaluator.compiled.TextContainsExpression
import ruleengine.evaluator.compiled.TextEndsWithExpression
import ruleengine.evaluator.compiled.TextStartsWithExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompiledExpressionOperatorsTest {
    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId(value = "purpose") to FieldDefinition(
                id = FieldId(value = "purpose"),
                type = FieldType.TEXT,
                normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase")),
                operators = setOf(OperatorId(value = "contains"))
            ),
            FieldId(value = "count") to FieldDefinition(
                id = FieldId(value = "count"),
                type = FieldType.INTEGER,
                normalizers = emptyList(),
                operators = setOf(OperatorId(value = "gt"))
            ),
            FieldId(value = "labels") to FieldDefinition(
                id = FieldId(value = "labels"),
                type = FieldType.STRING_SET,
                normalizers = listOf(NormalizerId(value = "lowercase")),
                operators = setOf(OperatorId(value = "containsAny"))
            )
        )
    )

    @Test
    fun `text contains startsWith endsWith`() {
        val ctx = RuleContext.of("purpose" to "  HelloWorld  ")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val contains = TextContainsExpression(field = FieldId(value = "purpose"), expectedNormalized = "helloworld")
        val starts = TextStartsWithExpression(field = FieldId(value = "purpose"), expectedNormalized = "hello")
        val ends = TextEndsWithExpression(field = FieldId(value = "purpose"), expectedNormalized = "world")

        assertTrue(actual = contains.evaluate(context = prepared))
        assertTrue(actual = starts.evaluate(context = prepared))
        assertTrue(actual = ends.evaluate(context = prepared))
    }

    @Test
    fun `integer comparisons`() {
        val ctx = RuleContext.of("count" to 5)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val gt = IntegerComparisonExpression(
            field = FieldId(value = "count"),
            expected = 3,
            op = IntegerComparisonOperator.GT
        )
        val eq = IntegerComparisonExpression(
            field = FieldId(value = "count"),
            expected = 5,
            op = IntegerComparisonOperator.EQ
        )
        val lt = IntegerComparisonExpression(
            field = FieldId(value = "count"),
            expected = 10,
            op = IntegerComparisonOperator.LT
        )

        assertTrue(actual = gt.evaluate(context = prepared))
        assertTrue(actual = eq.evaluate(context = prepared))
        assertTrue(actual = lt.evaluate(context = prepared))
    }

    @Test
    fun `string set contains any and all`() {
        val ctx = RuleContext.of("labels" to listOf("Risk", "Recurring"))
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val any = StringSetContainsAnyExpression(field = FieldId(value = "labels"), expectedNormalized = setOf("risk"))
        val all = StringSetContainsAllExpression(
            field = FieldId(value = "labels"),
            expectedNormalized = setOf("risk", "recurring")
        )

        assertTrue(actual = any.evaluate(context = prepared))
        assertTrue(actual = all.evaluate(context = prepared))
        assertFalse(
            actual = StringSetContainsAllExpression(
                field = FieldId(value = "labels"),
                expectedNormalized = setOf("risk", "other")
            ).evaluate(context = prepared)
        )
    }
}

