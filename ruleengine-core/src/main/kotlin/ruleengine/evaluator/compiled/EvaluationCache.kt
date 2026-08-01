package ruleengine.evaluator.compiled

import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.compiled.value.result.ExpressionValue

class EvaluationCache {
    private val aggregateValues = mutableMapOf<CompiledValueExpression, ExpressionValue>()

    fun getOrPut(key: CompiledValueExpression, compute: () -> ExpressionValue): ExpressionValue {
        return aggregateValues.getOrPut(key = key, defaultValue = compute)
    }
}
