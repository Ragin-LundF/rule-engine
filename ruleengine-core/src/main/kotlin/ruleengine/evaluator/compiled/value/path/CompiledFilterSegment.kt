package ruleengine.evaluator.compiled.value.path

import ruleengine.evaluator.compiled.CompiledExpression

data class CompiledFilterSegment(
    val expression: CompiledExpression
) : CompiledPathSegment
