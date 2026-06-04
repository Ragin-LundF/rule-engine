package ruleengine.evaluator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.evaluator.compiled.TextContainsExpression
import ruleengine.evaluator.compiled.TextStartsWithExpression
import ruleengine.evaluator.compiled.TextEndsWithExpression
import ruleengine.evaluator.compiled.IntegerComparisonExpression
import ruleengine.evaluator.compiled.IntegerComparisonOperator
import ruleengine.evaluator.compiled.StringSetContainsAnyExpression
import ruleengine.evaluator.compiled.StringSetContainsAllExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext

class CompiledExpressionOperatorsTest {
    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId("purpose") to FieldDefinition(id = FieldId("purpose"), type = FieldType.TEXT, normalizers = listOf(NormalizerId("trim"), NormalizerId("lowercase")), operators = setOf(OperatorId("contains"))),
            FieldId("count") to FieldDefinition(id = FieldId("count"), type = FieldType.INTEGER, normalizers = emptyList(), operators = setOf(OperatorId("gt"))),
            FieldId("labels") to FieldDefinition(id = FieldId("labels"), type = FieldType.STRING_SET, normalizers = listOf(NormalizerId("lowercase")), operators = setOf(OperatorId("containsAny")))
        )
    )

    @Test
    fun `text contains startsWith endsWith`() {
        val ctx = RuleContext.of("purpose" to "  HelloWorld  ")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val contains = TextContainsExpression(field = FieldId("purpose"), expectedNormalized = "helloworld")
        val starts = TextStartsWithExpression(field = FieldId("purpose"), expectedNormalized = "hello")
        val ends = TextEndsWithExpression(field = FieldId("purpose"), expectedNormalized = "world")

        assertTrue(contains.evaluate(prepared))
        assertTrue(starts.evaluate(prepared))
        assertTrue(ends.evaluate(prepared))
    }

    @Test
    fun `integer comparisons`() {
        val ctx = RuleContext.of("count" to 5)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val gt = IntegerComparisonExpression(field = FieldId("count"), expected = 3, op = IntegerComparisonOperator.GT)
        val eq = IntegerComparisonExpression(field = FieldId("count"), expected = 5, op = IntegerComparisonOperator.EQ)
        val lt = IntegerComparisonExpression(field = FieldId("count"), expected = 10, op = IntegerComparisonOperator.LT)

        assertTrue(gt.evaluate(prepared))
        assertTrue(eq.evaluate(prepared))
        assertTrue(lt.evaluate(prepared))
    }

    @Test
    fun `string set contains any and all`() {
        val ctx = RuleContext.of("labels" to listOf("Risk", "Recurring"))
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val any = StringSetContainsAnyExpression(field = FieldId("labels"), expectedNormalized = setOf("risk"))
        val all = StringSetContainsAllExpression(field = FieldId("labels"), expectedNormalized = setOf("risk", "recurring"))

        assertTrue(any.evaluate(prepared))
        assertTrue(all.evaluate(prepared))
        assertFalse(StringSetContainsAllExpression(field = FieldId("labels"), expectedNormalized = setOf("risk", "other")).evaluate(prepared))
    }
}


