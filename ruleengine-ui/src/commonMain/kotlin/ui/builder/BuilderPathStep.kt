package ui.builder


/**
 * One path segment plus the filters attached to it — mirrors an engine `FieldSegmentAst` followed by
 * zero or more `FilterSegmentAst`.
 *
 * Multiple [filters] on one step are joined with `and` in the generated DSL.
 */
data class BuilderPathStep(
    val name: String,
    val filters: List<BuilderFilter> = emptyList(),
)

/** Dotted names of a path, ignoring filters — the form used to look fields up in the catalog. */
val List<BuilderPathStep>.names: List<String>
    get() = map { it.name }

/**
 * Repoints the segment at [depth] to [name], **dropping everything below it**.
 *
 * The tail was resolved against the member that used to sit here, so it cannot survive the change:
 * `orders.items.price` repointed at depth 0 to `customer` is `customer`, never
 * `customer.items.price`. Filters on the segment go with it for the same reason.
 */
fun List<BuilderPathStep>.withSegmentName(depth: Int, name: String): List<BuilderPathStep> =
    take(n = depth) + BuilderPathStep(name = name)

/** Drops the segment at [depth], keeping the tail — the tail still resolves against its own parent. */
fun List<BuilderPathStep>.withoutSegment(depth: Int): List<BuilderPathStep> =
    filterIndexed { index, _ -> index != depth }
