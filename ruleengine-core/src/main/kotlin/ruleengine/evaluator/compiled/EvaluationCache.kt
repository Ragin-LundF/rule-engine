package ruleengine.evaluator.compiled

class EvaluationCache {
    private val aggregateValues = mutableMapOf<CompiledValueExpression, ExpressionValue>()

    fun getOrPut(key: CompiledValueExpression, compute: () -> ExpressionValue): ExpressionValue {
        return aggregateValues.getOrPut(key = key, defaultValue = compute)
    }
}
