package ruleengine.evaluator.compiled

sealed interface CompiledPathSegment

data class CompiledFieldSegment(
    val name: String
) : CompiledPathSegment

data class CompiledFilterSegment(
    val expression: CompiledExpression
) : CompiledPathSegment
