package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.CompiledExpression

data class CompiledFilterSegment(
    val expression: CompiledExpression
) : CompiledPathSegment
