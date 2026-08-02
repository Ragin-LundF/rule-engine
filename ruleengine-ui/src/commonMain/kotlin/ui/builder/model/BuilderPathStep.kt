package ui.builder.model


/**
 * One path segment plus whatever is applied to it — mirrors an engine `FieldSegmentAst` followed by
 * zero or more `FilterSegmentAst` / `SliceSegmentAst`.
 *
 * Multiple filters on one step are joined with `and` in the generated DSL. A slice among them takes
 * effect where it sits, which is why [decorations] is ordered rather than split into two lists.
 */
data class BuilderPathStep(
    val name: String,
    val decorations: List<BuilderPathDecoration> = emptyList(),
)

/** Builds a step carrying only filters, which is what most call sites want. */
fun pathStep(name: String, filters: List<BuilderFilter>): BuilderPathStep =
    BuilderPathStep(name = name, decorations = filters.map { BuilderPathDecoration.Filter(filter = it) })

/** The step's restrictions, in order, ignoring any slice between them. */
val BuilderPathStep.filters: List<BuilderFilter>
    get() = decorations.filterIsInstance<BuilderPathDecoration.Filter>().map { it.filter }

/** The step's slice, or null when it keeps every element. At most one is offered per segment. */
val BuilderPathStep.slice: BuilderPathDecoration.Slice?
    get() = decorations.filterIsInstance<BuilderPathDecoration.Slice>().firstOrNull()

/**
 * Replaces the step's filters, leaving a slice where it sits relative to them.
 *
 * Filters written before the slice stay before it: how many of them there are is what decides how
 * many elements the slice then sees.
 */
fun BuilderPathStep.withFilters(filters: List<BuilderFilter>): BuilderPathStep {
    val slicePosition = decorations.indexOfFirst { it is BuilderPathDecoration.Slice }
    val replacements = filters.map { filter -> BuilderPathDecoration.Filter(filter = filter) }
    if (slicePosition < 0) {
        return copy(decorations = replacements)
    }
    val before = decorations.take(n = slicePosition).count { it is BuilderPathDecoration.Filter }
    return copy(
        decorations = replacements.take(n = before) + decorations[slicePosition] + replacements.drop(n = before)
    )
}

/** Adds, replaces or removes the step's slice, keeping it after the filters already written. */
fun BuilderPathStep.withSlice(slice: BuilderPathDecoration.Slice?): BuilderPathStep {
    val withoutSlice = decorations.filter { it !is BuilderPathDecoration.Slice }
    if (slice == null) {
        return copy(decorations = withoutSlice)
    }
    return copy(decorations = withoutSlice + slice)
}

/** Dotted names of a path, ignoring decorations — the form used to look fields up in the catalog. */
val List<BuilderPathStep>.names: List<String>
    get() = map { it.name }

/**
 * Repoints the segment at [depth] to [name], **dropping everything below it**.
 *
 * The tail was resolved against the member that used to sit here, so it cannot survive the change:
 * `orders.items.price` repointed at depth 0 to `customer` is `customer`, never
 * `customer.items.price`. Anything applied to the segment goes with it for the same reason.
 */
fun List<BuilderPathStep>.withSegmentName(depth: Int, name: String): List<BuilderPathStep> =
    take(n = depth) + BuilderPathStep(name = name)

/** Drops the segment at [depth], keeping the tail — the tail still resolves against its own parent. */
fun List<BuilderPathStep>.withoutSegment(depth: Int): List<BuilderPathStep> =
    filterIndexed { index, _ -> index != depth }
