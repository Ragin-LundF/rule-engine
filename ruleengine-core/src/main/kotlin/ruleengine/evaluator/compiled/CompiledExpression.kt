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

class TextEqualsExpression(private val field: FieldId, private val expectedNormalized: String) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "equals", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = v.normalized == expectedNormalized
        trace?.exit(res)
        return res
    }
}

class TextContainsExpression(private val field: FieldId, private val expectedNormalized: String) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "contains", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = v.normalized.contains(expectedNormalized)
        trace?.exit(res)
        return res
    }
}

class TextStartsWithExpression(private val field: FieldId, private val expectedNormalized: String) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "startsWith", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = v.normalized.startsWith(expectedNormalized)
        trace?.exit(res)
        return res
    }
}

class TextEndsWithExpression(private val field: FieldId, private val expectedNormalized: String) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "endsWith", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = v.normalized.endsWith(expectedNormalized)
        trace?.exit(res)
        return res
    }
}

class TextInExpression(private val field: FieldId, private val expectedNormalized: Set<String>) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "in", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText ?: run { trace?.exit(false); return false }
        val res = expectedNormalized.contains(v.normalized)
        trace?.exit(res)
        return res
    }
}

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
            ComparisonOperator.EQ -> v.value.compareTo(expected) == 0
            ComparisonOperator.GT -> v.value.compareTo(expected) > 0
            ComparisonOperator.GTE -> v.value.compareTo(expected) >= 0
            ComparisonOperator.LT -> v.value.compareTo(expected) < 0
            ComparisonOperator.LTE -> v.value.compareTo(expected) <= 0
        }
        trace?.exit(res)
        return res
    }
}

enum class ComparisonOperator { EQ, GT, GTE, LT, LTE }

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
            IntegerComparisonOperator.EQ -> v.value == expected
            IntegerComparisonOperator.GT -> v.value > expected
            IntegerComparisonOperator.GTE -> v.value >= expected
            IntegerComparisonOperator.LT -> v.value < expected
            IntegerComparisonOperator.LTE -> v.value <= expected
        }
        trace?.exit(res)
        return res
    }
}

enum class IntegerComparisonOperator { EQ, GT, GTE, LT, LTE }

class StringSetContainsAnyExpression(
    private val field: FieldId,
    private val expectedNormalized: Set<String>
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "containsAny", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedStringSet ?: run { trace?.exit(false); return false }
        val res = v.normalized.any { it in expectedNormalized }
        trace?.exit(res)
        return res
    }
}

class StringSetContainsAllExpression(
    private val field: FieldId,
    private val expectedNormalized: Set<String>
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "containsAll", expected = expectedNormalized))
        val v = context.get(field) as? ruleengine.evaluator.context.PreparedStringSet ?: run { trace?.exit(false); return false }
        val res = expectedNormalized.all { it in v.normalized }
        trace?.exit(res)
        return res
    }
}

class AndExpression(children: List<CompiledExpression>) : CompiledExpression {
    private val orderedChildren = children.sortedBy { it.cost }
    override val cost: EvaluationCost = orderedChildren.firstOrNull()?.cost ?: EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.AND))
        for (c in orderedChildren) {
            val res = c.evaluate(context, trace)
            if (!res) { trace?.exit(false); return false }
        }
        trace?.exit(true)
        return true
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
        trace?.exit(false)
        return false
    }
}

class NotExpression(private val child: CompiledExpression) : CompiledExpression {
    override val cost: EvaluationCost = child.cost

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.NOT))
        val resChild = child.evaluate(context, trace)
        val res = !resChild
        trace?.exit(res)
        return res
    }
}
