package ruleengine.evaluator.compiled.value

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.DateExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValues
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.ObjectExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * The comparison used whenever an operand is an expression rather than a bare field — an aggregate
 * (`count(...)`, `sum(...)`), arithmetic, or another field. Plain `field op literal` conditions
 * compile to the dedicated leaves instead ([IntegerComparisonExpression] and friends).
 *
 * @param label Rendered text of the left operand, used to name the condition in the trace. Empty for
 *   the per-element predicate inside a filter segment, which is evaluated with no collector at all.
 */
class ComparisonCompiledExpression(
    private val left: CompiledValueExpression,
    private val operator: ComparisonOperatorAst,
    private val right: CompiledValueExpression,
    override val cost: EvaluationCost,
    private val label: String = ""
) : CompiledExpression {
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        val leftValue = left.evaluate(context = context)
        val rightValue = right.evaluate(context = context)

        // Entered after both operands are evaluated, unlike the other leaves: the values being
        // compared are what make the node worth reading, and they are not known any earlier.
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = label,
                operator = operator.name,
                expected = plainValue(value = rightValue),
                actual = plainValue(value = leftValue)
            )
        )
        val verdict = verdictFor(leftValue = leftValue, rightValue = rightValue)
        trace?.exit(verdict = verdict)
        return verdict
    }

    /**
     * The comparison's answer, or [ConditionVerdict.UNKNOWN] when an operand is not there to compare.
     *
     * This is one of the two places an unknown is born. An absent field, an aggregate that reduced to
     * nothing (`avg` over an empty collection) and a variable no rule has published all arrive here as
     * [MissingExpressionValue], and none of them is a reason to answer "no".
     */
    private fun verdictFor(leftValue: ExpressionValue, rightValue: ExpressionValue): ConditionVerdict {
        if (leftValue is MissingExpressionValue || rightValue is MissingExpressionValue) {
            return ConditionVerdict.UNKNOWN
        }
        return ConditionVerdict.of(
            value = compareValues(leftValue = leftValue, operator = operator, rightValue = rightValue)
        )
    }

    /**
     * Unwraps to the value Jackson should see. An [ExpressionValue] is a wrapper, so handing one
     * straight to the trace would serialize as `{"value":500}` and display as
     * `NumberExpressionValue(value=500)`.
     */
    private fun plainValue(value: ExpressionValue): Any? {
        return when (value) {
            is NumberExpressionValue -> value.value
            is TextExpressionValue -> value.value
            is BooleanExpressionValue -> value.value
            is ArrayExpressionValue -> value.values.map { element -> plainValue(value = element) }
            is DateExpressionValue -> value.value.toString()
            is ObjectExpressionValue, MissingExpressionValue -> null
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun compareValues(
        leftValue: ExpressionValue,
        operator: ComparisonOperatorAst,
        rightValue: ExpressionValue
    ): Boolean {
        if (operator == ComparisonOperatorAst.CONTAINS) {
            return containsValue(leftValue = leftValue, rightValue = rightValue)
        }
        // Membership is `contains` with the operands the other way round, so it reuses the same
        // value equality — which is what makes `1` find `1.0` here as it does everywhere else.
        if (operator == ComparisonOperatorAst.IN) {
            return memberOf(element = leftValue, source = rightValue)
        }
        // Checked before the type dispatch below, so a date compares as a date whichever side it is
        // on. The other operand may be text: a member of a collection carries no declared type, so
        // `orders[].shippedAt > registeredAt` has a date on one side and an ISO string on the other.
        if (leftValue is DateExpressionValue || rightValue is DateExpressionValue) {
            return compareDates(leftValue = leftValue, operator = operator, rightValue = rightValue)
        }
        return when (leftValue) {
            is NumberExpressionValue if rightValue is NumberExpressionValue -> {
                val cmp = leftValue.value.compareTo(rightValue.value)
                when (operator) {
                    ComparisonOperatorAst.EQ -> cmp == 0
                    ComparisonOperatorAst.NEQ -> cmp != 0
                    ComparisonOperatorAst.GT -> cmp > 0
                    ComparisonOperatorAst.GTE -> cmp >= 0
                    ComparisonOperatorAst.LT -> cmp < 0
                    ComparisonOperatorAst.LTE -> cmp <= 0
                    // Unreachable: `contains` and `in` both return above, before the operand types
                    // are examined.
                    ComparisonOperatorAst.CONTAINS, ComparisonOperatorAst.IN -> false
                }
            }

            is TextExpressionValue if rightValue is TextExpressionValue -> {
                when (operator) {
                    ComparisonOperatorAst.EQ -> leftValue.value == rightValue.value
                    ComparisonOperatorAst.NEQ -> leftValue.value != rightValue.value
                    else -> false
                }
            }

            is BooleanExpressionValue if rightValue is BooleanExpressionValue -> {
                when (operator) {
                    ComparisonOperatorAst.EQ -> leftValue.value == rightValue.value
                    ComparisonOperatorAst.NEQ -> leftValue.value != rightValue.value
                    // Ordering has no meaning for booleans.
                    else -> false
                }
            }

            else -> false
        }
    }

    /**
     * Whether [element] is one of the values [source] holds.
     *
     * A single value counts as a source of one. A path that selects exactly one element collapses to
     * a scalar, so without this `invoices[customerId in priorityCustomerIds]` would stop matching as
     * soon as the document happened to carry a single priority customer.
     *
     * A missing source never reaches here — [verdictFor] answers unknown for it — which is what makes
     * an empty membership source select nothing without claiming the answer was decided.
     */
    private fun memberOf(element: ExpressionValue, source: ExpressionValue): Boolean {
        return when (source) {
            is ArrayExpressionValue -> ExpressionValues.arrayContains(container = source, element = element)
            else -> ExpressionValues.equalsByValue(left = source, right = element)
        }
    }

    /**
     * Two calendar dates, with either side allowed to arrive as ISO-8601 text.
     *
     * A value that is neither is not comparable to a date, so the comparison is false rather than an
     * error — the same answer a missing operand gives.
     */
    private fun compareDates(
        leftValue: ExpressionValue,
        operator: ComparisonOperatorAst,
        rightValue: ExpressionValue
    ): Boolean {
        val left = ExpressionValues.asDate(value = leftValue) ?: return false
        val right = ExpressionValues.asDate(value = rightValue) ?: return false
        val cmp = left.compareTo(right)
        return when (operator) {
            ComparisonOperatorAst.EQ -> cmp == 0
            ComparisonOperatorAst.NEQ -> cmp != 0
            ComparisonOperatorAst.GT -> cmp > 0
            ComparisonOperatorAst.GTE -> cmp >= 0
            ComparisonOperatorAst.LT -> cmp < 0
            ComparisonOperatorAst.LTE -> cmp <= 0
            // Unreachable: `contains` and `in` both return before the operand types are examined.
            ComparisonOperatorAst.CONTAINS, ComparisonOperatorAst.IN -> false
        }
    }

    /**
     * `contains`, dispatched on the left operand's runtime type: membership for a list, substring for
     * text.
     *
     * One word, two meanings, chosen deliberately. `contains` already means substring everywhere else
     * in the engine, so `purpose contains "rent"` and `$purposeCopy contains "rent"` agree — which is
     * what a reader assumes. Membership is the natural reading of the same word over a list.
     *
     * A missing left operand never reaches here: [verdictFor] answers unknown for it, and a rule that
     * declares no `not_exists` branch reads that as false — which is what still lets
     * `not $topics contains "billing"` pass before anything has been added.
     */
    private fun containsValue(leftValue: ExpressionValue, rightValue: ExpressionValue): Boolean {
        return when {
            leftValue is ArrayExpressionValue ->
                ExpressionValues.arrayContains(container = leftValue, element = rightValue)

            leftValue is TextExpressionValue && rightValue is TextExpressionValue ->
                leftValue.value.contains(other = rightValue.value)

            else -> false
        }
    }
}
