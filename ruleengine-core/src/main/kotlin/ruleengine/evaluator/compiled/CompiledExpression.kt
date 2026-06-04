package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.core.domain.FieldId
import ruleengine.evaluator.trace.NodeMeta
import ruleengine.evaluator.trace.NodeType
import ruleengine.evaluator.trace.TraceCollector
import java.math.BigDecimal

enum class EvaluationCost { VERY_CHEAP, CHEAP, MEDIUM, EXPENSIVE }

interface CompiledExpression {
    val cost: EvaluationCost
    fun evaluate(context: PreparedRuleContext, trace: TraceCollector? = null): Boolean
}

// ---------------------------------------------------------------------------
// Text expressions
// ---------------------------------------------------------------------------

class TextEqualsExpression(
    private val field: FieldId,
    private val expectedNormalized: String,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value,
            operator = if (ignoreCase) "equalsIgnoreCase" else "equals", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = if (ignoreCase) v.normalized.equals(expectedNormalized, ignoreCase = true) else v.normalized == expectedNormalized
        trace?.exit(res); return res
    }
}

class TextContainsExpression(
    private val field: FieldId,
    private val expectedNormalized: String,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value,
            operator = if (ignoreCase) "containsIgnoreCase" else "contains", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = if (ignoreCase) v.normalized.contains(expectedNormalized, ignoreCase = true) else v.normalized.contains(expectedNormalized)
        trace?.exit(res); return res
    }
}

class TextStartsWithExpression(
    private val field: FieldId,
    private val expectedNormalized: String,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value,
            operator = if (ignoreCase) "startsWithIgnoreCase" else "startsWith", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = if (ignoreCase) v.normalized.startsWith(expectedNormalized, ignoreCase = true) else v.normalized.startsWith(expectedNormalized)
        trace?.exit(res); return res
    }
}

class TextEndsWithExpression(
    private val field: FieldId,
    private val expectedNormalized: String,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value,
            operator = if (ignoreCase) "endsWithIgnoreCase" else "endsWith", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = if (ignoreCase) v.normalized.endsWith(expectedNormalized, ignoreCase = true) else v.normalized.endsWith(expectedNormalized)
        trace?.exit(res); return res
    }
}

class TextInExpression(
    private val field: FieldId,
    private val expectedNormalized: Set<String>,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP
    private val matchSet: Set<String> =
        if (ignoreCase) expectedNormalized.mapTo(HashSet()) { it.lowercase() } else expectedNormalized
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value,
            operator = if (ignoreCase) "inIgnoreCase" else "in", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val key = if (ignoreCase) v.normalized.lowercase() else v.normalized
        val res = key in matchSet
        trace?.exit(res); return res
    }
}

/**
 * Matches the field's original (pre-normalization) value against a compiled regex.
 * ignoreCase is baked into the Regex at compile time via RegexOption.IGNORE_CASE.
 */
class TextRegexExpression(
    private val field: FieldId,
    private val pattern: Regex
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "regex", expected = pattern.pattern))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = pattern.containsMatchIn(v.original)
        trace?.exit(res); return res
    }
}

// ---------------------------------------------------------------------------
// Numeric comparison expressions
// ---------------------------------------------------------------------------

class DecimalComparisonExpression(
    private val field: FieldId,
    private val expected: BigDecimal,
    private val op: ComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = op.name, expected = expected))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedDecimal ?: run { trace?.exit(false); return false }
        val res = when (op) {
            ComparisonOperator.EQ  -> v.value.compareTo(expected) == 0
            ComparisonOperator.GT  -> v.value.compareTo(expected) > 0
            ComparisonOperator.GTE -> v.value.compareTo(expected) >= 0
            ComparisonOperator.LT  -> v.value.compareTo(expected) < 0
            ComparisonOperator.LTE -> v.value.compareTo(expected) <= 0
        }
        trace?.exit(res); return res
    }
}

enum class ComparisonOperator { EQ, GT, GTE, LT, LTE }

/** Inclusive range check: low <= field <= high */
class DecimalBetweenExpression(
    private val field: FieldId,
    private val low: BigDecimal,
    private val high: BigDecimal
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "between", expected = "\$low..\$high"))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedDecimal ?: run { trace?.exit(false); return false }
        val res = v.value.compareTo(low) >= 0 && v.value.compareTo(high) <= 0
        trace?.exit(res); return res
    }
}

class IntegerComparisonExpression(
    private val field: FieldId,
    private val expected: Long,
    private val op: IntegerComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = op.name, expected = expected))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedInteger ?: run { trace?.exit(false); return false }
        val res = when (op) {
            IntegerComparisonOperator.EQ  -> v.value == expected
            IntegerComparisonOperator.GT  -> v.value > expected
            IntegerComparisonOperator.GTE -> v.value >= expected
            IntegerComparisonOperator.LT  -> v.value < expected
            IntegerComparisonOperator.LTE -> v.value <= expected
        }
        trace?.exit(res); return res
    }
}

enum class IntegerComparisonOperator { EQ, GT, GTE, LT, LTE }

/** Inclusive range check: low <= field <= high */
class IntegerBetweenExpression(
    private val field: FieldId,
    private val low: Long,
    private val high: Long
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "between", expected = "\$low..\$high"))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedInteger ?: run { trace?.exit(false); return false }
        val res = v.value in low..high
        trace?.exit(res); return res
    }
}

// ---------------------------------------------------------------------------
// String-set expressions
// ---------------------------------------------------------------------------

class StringSetContainsAnyExpression(
    private val field: FieldId,
    private val expectedNormalized: Set<String>,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM
    private val matchSet: Set<String> =
        if (ignoreCase) expectedNormalized.mapTo(HashSet()) { it.lowercase() } else expectedNormalized
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "containsAny", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedStringSet ?: run { trace?.exit(false); return false }
        val checkSet = if (ignoreCase) v.normalized.mapTo(HashSet()) { it.lowercase() } else v.normalized
        val res = checkSet.any { it in matchSet }
        trace?.exit(res); return res
    }
}

class StringSetContainsAllExpression(
    private val field: FieldId,
    private val expectedNormalized: Set<String>,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM
    private val matchSet: Set<String> =
        if (ignoreCase) expectedNormalized.mapTo(HashSet()) { it.lowercase() } else expectedNormalized
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "containsAll", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedStringSet ?: run { trace?.exit(false); return false }
        val checkSet = if (ignoreCase) v.normalized.mapTo(HashSet()) { it.lowercase() } else v.normalized
        val res = matchSet.all { it in checkSet }
        trace?.exit(res); return res
    }
}

// ---------------------------------------------------------------------------
// Logical combinators
// ---------------------------------------------------------------------------

class AndExpression(children: List<CompiledExpression>) : CompiledExpression {
    private val orderedChildren = children.sortedBy { it.cost }
    override val cost: EvaluationCost = orderedChildren.firstOrNull()?.cost ?: EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.AND))
        for (c in orderedChildren) {
            val res = c.evaluate(context, trace)
            if (!res) { trace?.exit(false); return false }
        }
        trace?.exit(true); return true
    }
}

class OrExpression(private val children: List<CompiledExpression>) : CompiledExpression {
    override val cost: EvaluationCost = children.firstOrNull()?.cost ?: EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.OR))
        for (c in children) {
            val res = c.evaluate(context, trace)
            if (res) { trace?.exit(true); return true }
        }
        trace?.exit(false); return false
    }
}

class NotExpression(private val child: CompiledExpression) : CompiledExpression {
    override val cost: EvaluationCost = child.cost
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.NOT))
        val resChild = child.evaluate(context, trace)
        val res = !resChild
        trace?.exit(res); return res
    }
}
