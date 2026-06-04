package ruleengine.evaluator.compiled

enum class EvaluationCost { VERY_CHEAP, CHEAP, MEDIUM, EXPENSIVE }

interface CompiledExpression {
    val cost: EvaluationCost
    fun evaluate(
        context: ruleengine.evaluator.context.PreparedRuleContext,
        trace: ruleengine.evaluator.trace.TraceCollector? = null
    ): Boolean
}

enum class ComparisonOperator { EQ, GT, GTE, LT, LTE }

enum class IntegerComparisonOperator { EQ, GT, GTE, LT, LTE }

