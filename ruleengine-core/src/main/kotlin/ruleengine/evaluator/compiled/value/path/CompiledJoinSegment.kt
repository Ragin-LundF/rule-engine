package ruleengine.evaluator.compiled.value.path

import ruleengine.evaluator.compiled.value.KeyedSumCompiledValueExpression

/**
 * Rows joined on [key]: one per key any source mentions, holding the key and, under each alias of
 * [sources], that source's total for the key — zero when the source does not mention it.
 *
 * Always the first segment of its path. [sources] keeps the order the author wrote, which is the
 * order keys are first seen in.
 */
data class CompiledJoinSegment(
    val key: String,
    val sources: Map<String, KeyedSumCompiledValueExpression.Source>
) : CompiledPathSegment
