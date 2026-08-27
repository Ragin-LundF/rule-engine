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
 * @param leftYieldsCollection Whether the left operand is collection-valued by its declared shape.
 *   Only `contains` reads it, and only because the runtime type cannot answer the question: a path
 *   selecting exactly one element arrives as a scalar, which would otherwise turn a membership test
 *   into a substring test for that one record.
 * @param ignoreCase The `ignoreCase` modifier. Applied by folding both operands once, before any
 *   operator looks at them — see [foldCase].
 */
class ComparisonCompiledExpression(
    private val left: CompiledValueExpression,
    private val operator: ComparisonOperatorAst,
    private val right: CompiledValueExpression,
    override val cost: EvaluationCost,
    private val label: String = "",
    private val leftYieldsCollection: Boolean = false,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        val leftValue = foldCase(value = left.evaluate(context = context))
        val rightValue = foldCase(value = right.evaluate(context = context))

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
     * The comparison's answer, or [ConditionVerdict.UNKNOWN] when the operands cannot be compared.
     *
     * This is one of the two places an unknown is born, and there are two ways to reach it. An absent
     * field, an aggregate that reduced to nothing (`avg` over an empty collection) and a variable no
     * rule has published all arrive as [MissingExpressionValue]; none of them is a reason to answer
     * "no".
     *
     * The second way is a pair of operands that are both present and still not comparable — a number
     * against a text, two lists, an ordering over text. [compareValues] answers `null` for those, and
     * they are undecidable for the same reason: the engine has no reading of the question, so "false"
     * would be an answer it cannot justify. It was one, and `count(x) != "abc"` therefore claimed the
     * two were equal.
     */
    private fun verdictFor(leftValue: ExpressionValue, rightValue: ExpressionValue): ConditionVerdict {
        if (leftValue is MissingExpressionValue || rightValue is MissingExpressionValue) {
            return ConditionVerdict.UNKNOWN
        }
        val result = compareValues(leftValue = leftValue, operator = operator, rightValue = rightValue)
            ?: return ConditionVerdict.UNKNOWN
        return ConditionVerdict.of(value = result)
    }

    /**
     * Lower-cases every text the value holds, when [ignoreCase] is set.
     *
     * Done once per operand rather than per operator, which is what makes `==`, `!=`, `contains` —
     * both its substring and its membership reading — and `in` all honour the modifier from one place.
     * Adding a case-insensitive arm to each of `compareValues`, `containsValue`, `memberOf` and
     * `ExpressionValues.equalsByValue` would be four chances for them to disagree.
     *
     * Recursive into an array so a membership source folds element by element; every other kind is
     * returned untouched, since case is a property of text alone.
     */
    private fun foldCase(value: ExpressionValue): ExpressionValue {
        if (!ignoreCase) {
            return value
        }
        return when (value) {
            is TextExpressionValue -> TextExpressionValue(value = value.value.lowercase())
            is ArrayExpressionValue -> ArrayExpressionValue(values = value.values.map { element ->
                foldCase(value = element)
            })

            else -> value
        }
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

    /**
     * The comparison's answer, or null when this pair of operands has no comparison.
     *
     * `contains` and `in` are membership tests and always decide: "not among these values" is an
     * answer, not a failure to read the question.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun compareValues(
        leftValue: ExpressionValue,
        operator: ComparisonOperatorAst,
        rightValue: ExpressionValue
    ): Boolean? {
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
                    // Text has no ordering here. `ExpressionValues.compareByValue` does order it for
                    // `sortBy`, but that is a total order invented to make sorting defined, not a
                    // claim that one string is less than another.
                    else -> null
                }
            }

            is BooleanExpressionValue if rightValue is BooleanExpressionValue -> {
                when (operator) {
                    ComparisonOperatorAst.EQ -> leftValue.value == rightValue.value
                    ComparisonOperatorAst.NEQ -> leftValue.value != rightValue.value
                    // Ordering has no meaning for booleans.
                    else -> null
                }
            }

            // Mismatched kinds, two lists, a structure: present on both sides and still not a
            // question this engine can answer.
            else -> null
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
     * A value that is neither is not comparable to a date, so the comparison is undecided rather than
     * an error — the same answer a missing operand gives.
     */
    private fun compareDates(
        leftValue: ExpressionValue,
        operator: ComparisonOperatorAst,
        rightValue: ExpressionValue
    ): Boolean? {
        val left = ExpressionValues.asDate(value = leftValue) ?: return null
        val right = ExpressionValues.asDate(value = rightValue) ?: return null
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
     *
     * [leftYieldsCollection] decides the reading before the runtime type is consulted. A path down a
     * collection is a membership test however many elements one record happens to carry, so
     * `orders[...].tags contains "prem"` cannot become a substring test just because a record has a
     * single tag.
     */
    private fun containsValue(leftValue: ExpressionValue, rightValue: ExpressionValue): Boolean {
        if (leftYieldsCollection) {
            return when (leftValue) {
                is ArrayExpressionValue ->
                    ExpressionValues.arrayContains(container = leftValue, element = rightValue)

                // One element, collapsed to a scalar on the way here — a source of one.
                else -> ExpressionValues.equalsByValue(left = leftValue, right = rightValue)
            }
        }
        return when {
            leftValue is ArrayExpressionValue ->
                ExpressionValues.arrayContains(container = leftValue, element = rightValue)

            leftValue is TextExpressionValue && rightValue is TextExpressionValue ->
                leftValue.value.contains(other = rightValue.value)

            else -> false
        }
    }
}
