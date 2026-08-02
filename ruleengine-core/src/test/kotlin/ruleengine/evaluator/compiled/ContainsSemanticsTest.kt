package ruleengine.evaluator.compiled

import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.evaluator.compiled.logic.AndExpression
import ruleengine.evaluator.compiled.logic.NotExpression
import ruleengine.evaluator.compiled.text.TextContainsExpression
import ruleengine.evaluator.compiled.value.ComparisonCompiledExpression
import ruleengine.evaluator.compiled.value.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.ObjectExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.trace.TraceCollector
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `contains` in the expression path, one case per row of its semantics table.
 *
 * Driven through [ComparisonCompiledExpression] with literal operands rather than through the parser,
 * so each runtime type pairing can be stated directly — several of them are unreachable from any DSL
 * a validator would accept, and still have to be total.
 */
class ContainsSemanticsTest {

    private val context = PreparedRuleContext.prepare(
        ctx = RuleContext.of("text" to "irrelevant"),
        schema = FieldSchema(name = "empty", fields = emptyMap()),
    )

    private fun text(value: String) = TextExpressionValue(value = value)
    private fun number(value: String) = NumberExpressionValue(value = BigDecimal(value))
    private fun list(vararg values: ExpressionValue) = ArrayExpressionValue(values = values.toList())

    private fun contains(left: ExpressionValue, right: ExpressionValue): Boolean =
        comparison(left = left, right = right).evaluate(context = context, trace = null)

    private fun comparison(left: ExpressionValue, right: ExpressionValue) = ComparisonCompiledExpression(
        left = LiteralCompiledValueExpression(value = left),
        operator = ComparisonOperatorAst.CONTAINS,
        right = LiteralCompiledValueExpression(value = right),
        cost = EvaluationCost.CHEAP,
    )

    // ── list membership ───────────────────────────────────────────────────────

    @Test
    fun `a list containing the value matches`() {
        assertTrue(actual = contains(left = list(text("billing"), text("cards")), right = text("cards")))
    }

    @Test
    fun `a list not containing the value does not match`() {
        assertFalse(actual = contains(left = list(text("billing")), right = text("cards")))
    }

    @Test
    fun `an empty list matches nothing`() {
        assertFalse(actual = contains(left = list(), right = text("billing")))
    }

    /** Numbers compare by value, so how the two sides were written must not matter. */
    @Test
    fun `a list holding a number matches the same number written differently`() {
        assertTrue(actual = contains(left = list(number("1")), right = number("1.0")))
    }

    @Test
    fun `a list holding a boolean matches that boolean`() {
        assertTrue(
            actual = contains(
                left = list(BooleanExpressionValue(value = true)),
                right = BooleanExpressionValue(value = true),
            )
        )
    }

    @Test
    fun `a list does not match a value of a different type`() {
        assertFalse(actual = contains(left = list(text("1")), right = number("1")))
    }

    @Test
    fun `a list does not match another list`() {
        assertFalse(actual = contains(left = list(text("a")), right = list(text("a"))))
    }

    // ── text substring ────────────────────────────────────────────────────────

    @Test
    fun `text containing the substring matches`() {
        assertTrue(actual = contains(left = text("please refund"), right = text("refund")))
    }

    @Test
    fun `text not containing the substring does not match`() {
        assertFalse(actual = contains(left = text("please refund"), right = text("parcel")))
    }

    @Test
    fun `text substring matching is case-sensitive on the expression path`() {
        assertFalse(actual = contains(left = text("Please Refund"), right = text("refund")))
    }

    // ── everything else is false ──────────────────────────────────────────────

    /** The row the whole feature rests on: an unset accumulator makes the guard pass. */
    @Test
    fun `a missing left operand does not match, so its negation does`() {
        val expression = comparison(left = MissingExpressionValue, right = text("billing"))

        assertFalse(actual = expression.evaluate(context = context, trace = null))
        assertTrue(actual = NotExpression(child = expression).evaluate(context = context, trace = null))
    }

    @Test
    fun `a missing right operand does not match`() {
        assertFalse(actual = contains(left = list(text("billing")), right = MissingExpressionValue))
    }

    @Test
    fun `a number left operand does not match`() {
        assertFalse(actual = contains(left = number("1"), right = number("1")))
    }

    @Test
    fun `a boolean left operand does not match`() {
        assertFalse(
            actual = contains(
                left = BooleanExpressionValue(value = true),
                right = BooleanExpressionValue(value = true),
            )
        )
    }

    @Test
    fun `a structure left operand does not match`() {
        assertFalse(actual = contains(left = ObjectExpressionValue(value = mapOf("a" to 1)), right = text("a")))
    }

    // ── cost ──────────────────────────────────────────────────────────────────

    /**
     * The guard has to be cheaper than the text matching it guards, or `AndExpression`'s cost sort
     * would run the expensive side first and the whole optimisation would be lost.
     */
    @Test
    fun `a negated list guard is cheaper than a text contains`() {
        val guard = NotExpression(child = comparison(left = list(), right = text("billing")))
        val textMatch = TextContainsExpression(
            field = FieldId(value = "text"),
            expectedNormalized = "refund",
            ignoreCase = false,
        )

        assertTrue(actual = guard.cost < textMatch.cost)
        assertEquals(expected = EvaluationCost.CHEAP, actual = guard.cost)
    }

    @Test
    fun `an and evaluates the cheaper guard first regardless of source order`() {
        val order = mutableListOf<String>()
        val expensive = RecordingExpression(name = "expensive", cost = EvaluationCost.MEDIUM, order = order)
        val guard = RecordingExpression(name = "guard", cost = EvaluationCost.CHEAP, order = order)

        AndExpression(children = listOf(expensive, guard)).evaluate(context = context, trace = null)

        assertEquals(expected = listOf("guard", "expensive"), actual = order)
    }

    private class RecordingExpression(
        private val name: String,
        override val cost: EvaluationCost,
        private val order: MutableList<String>,
    ) : CompiledExpression {
        override fun evaluate(
            context: PreparedRuleContext,
            trace: TraceCollector?,
        ): Boolean {
            order += name
            return true
        }
    }
}
