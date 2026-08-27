package ruleengine.evaluator.compiled

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.evaluator.compiled.value.ComparisonCompiledExpression
import ruleengine.evaluator.compiled.value.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.DateExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.ObjectExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two operands that are both present and still not comparable are undecidable, not false.
 *
 * `compareValues` used to end in `else -> false`, which made `!=` the wrong way round: a number
 * against a text answered "false", i.e. *they are equal*. Ordering over text and equality between two
 * lists had the same shape. None of those is a "no" the engine can justify, so each is now an
 * [ConditionVerdict.UNKNOWN] — the same answer a missing operand already gave.
 *
 * Driven through [ComparisonCompiledExpression] with literal operands, so each pairing can be stated
 * directly: several are unreachable from any DSL the validator accepts and still have to be total.
 */
class IncomparableOperandsTest {

    private val context = PreparedRuleContext.prepare(
        ctx = RuleContext.of("text" to "irrelevant"),
        schema = FieldSchema(name = "empty", fields = emptyMap()),
    )

    private fun text(value: String) = TextExpressionValue(value = value)
    private fun number(value: String) = NumberExpressionValue(value = BigDecimal(value))
    private fun list(vararg values: ExpressionValue) = ArrayExpressionValue(values = values.toList())

    // ── the regression: inequality across kinds must not claim equality ───────

    @Test
    fun `inequality between a number and a text is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(left = number("5"), operator = ComparisonOperatorAst.NEQ, right = text("abc")),
            message = "answering false here claimed a number and a text were equal"
        )
    }

    @Test
    fun `equality between a number and a text is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(left = number("5"), operator = ComparisonOperatorAst.EQ, right = text("abc")),
            message = "the two sides cannot be compared, so neither == nor != can decide"
        )
    }

    @Test
    fun `equality between two lists is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                left = list(text("a")),
                operator = ComparisonOperatorAst.EQ,
                right = list(text("a"))
            ),
            message = "the engine has no equality over lists; it must not answer 'not equal'"
        )
    }

    @Test
    fun `ordering over text is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(left = text("abc"), operator = ComparisonOperatorAst.LT, right = text("def")),
            message = "sortBy's total order exists to make sorting defined, not to order text here"
        )
    }

    @Test
    fun `ordering over booleans is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                left = BooleanExpressionValue(value = true),
                operator = ComparisonOperatorAst.GT,
                right = BooleanExpressionValue(value = false)
            ),
            message = "ordering has no meaning for booleans"
        )
    }

    @Test
    fun `a structure operand is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                left = ObjectExpressionValue(value = mapOf("a" to 1)),
                operator = ComparisonOperatorAst.EQ,
                right = number("1")
            ),
            message = "a structure has no scalar identity to compare"
        )
    }

    @Test
    fun `a date compared against unparseable text is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                left = text("not-a-date"),
                operator = ComparisonOperatorAst.LT,
                right = DateExpressionValue(value = LocalDate.of(2026, 1, 1))
            ),
            message = "text that is not ISO-8601 is not a date, so the comparison cannot be read"
        )
    }

    // ── comparable pairs still decide, in both directions ─────────────────────

    @Test
    fun `two numbers still decide`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(left = number("5"), operator = ComparisonOperatorAst.GT, right = number("1")),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(left = number("1"), operator = ComparisonOperatorAst.GT, right = number("5")),
        )
    }

    @Test
    fun `two texts still decide equality`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(left = text("a"), operator = ComparisonOperatorAst.NEQ, right = text("b")),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(left = text("a"), operator = ComparisonOperatorAst.NEQ, right = text("a")),
        )
    }

    /**
     * Membership is not affected: "not among these values" is a decided answer, so `contains` and
     * `in` keep returning FALSE rather than becoming undecidable.
     */
    @Test
    fun `membership across kinds still decides`() {
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(
                left = list(text("a")),
                operator = ComparisonOperatorAst.CONTAINS,
                right = number("1")
            ),
            message = "a list that does not hold the value answers no, it does not fail to answer"
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(
                left = number("1"),
                operator = ComparisonOperatorAst.IN,
                right = list(text("a"))
            ),
            message = "membership stays decided in the other direction too"
        )
    }

    private fun verdict(
        left: ExpressionValue,
        operator: ComparisonOperatorAst,
        right: ExpressionValue
    ): ConditionVerdict = ComparisonCompiledExpression(
        left = LiteralCompiledValueExpression(value = left),
        operator = operator,
        right = LiteralCompiledValueExpression(value = right),
        cost = EvaluationCost.CHEAP,
    ).evaluate(context = context, trace = null)
}
