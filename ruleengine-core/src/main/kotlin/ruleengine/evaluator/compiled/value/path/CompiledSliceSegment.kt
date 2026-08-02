package ruleengine.evaluator.compiled.value.path

/**
 * Keeps at most [count] elements, from the start of source order or from its end.
 *
 * Applied to the raw element list, before anything is converted to an
 * [ruleengine.evaluator.compiled.value.result.ExpressionValue]: `sum(take(orders, 3).total)` should
 * touch three orders, not convert every order and discard most of the work.
 */
data class CompiledSliceSegment(
    val fromEnd: Boolean,
    val count: Int
) : CompiledPathSegment
