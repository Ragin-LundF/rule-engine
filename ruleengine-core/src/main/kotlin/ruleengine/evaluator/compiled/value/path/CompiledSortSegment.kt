package ruleengine.evaluator.compiled.value.path

/**
 * Puts the selected elements in order, by [member] or by the elements themselves.
 *
 * Applied to the raw element list, before anything is converted to an
 * [ruleengine.evaluator.compiled.value.result.ExpressionValue], so `take(sortBy(orders, "total",
 * desc), 3).total` orders raw elements and converts three of them rather than all of them.
 *
 * [member] is already resolved to its canonical name: the runtime reads raw input maps, which are
 * keyed by the canonical member name and know nothing of the aliases the schema declares.
 */
data class CompiledSortSegment(
    val member: String?,
    val descending: Boolean
) : CompiledPathSegment
