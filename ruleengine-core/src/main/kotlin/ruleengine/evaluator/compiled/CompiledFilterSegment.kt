package ruleengine.evaluator.compiled

data class CompiledFilterSegment(
    val expression: CompiledExpression
) : CompiledPathSegment
