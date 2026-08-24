package ui.builder.model


/**
 * One path segment plus whatever is applied to it — mirrors an engine `FieldSegmentAst` followed by
 * zero or more `FilterSegmentAst` / `SliceSegmentAst` / `SortSegmentAst`.
 *
 * Multiple filters on one step are joined with `and` in the generated DSL. A slice or a sort among
 * them takes effect where it sits, which is why [decorations] is ordered rather than split apart.
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

/** The step's ordering, or null when it keeps source order. At most one is offered per segment. */
val BuilderPathStep.sort: BuilderPathDecoration.Sort?
    get() = decorations.filterIsInstance<BuilderPathDecoration.Sort>().firstOrNull()

/**
 * Replaces the step's filters, leaving everything else where it sits relative to them.
 *
 * Filters written before a slice or a sort stay before it: how many of them there are is what
 * decides which elements the slice then sees, and in which order. Written as a walk rather than by
 * recomputing one decoration's index, so a second kind of decoration cannot be dropped here — which
 * is exactly what the earlier slice-only version would have done to a sort.
 */
fun BuilderPathStep.withFilters(filters: List<BuilderFilter>): BuilderPathStep {
    val replacements = filters.map { filter -> BuilderPathDecoration.Filter(filter = filter) }
    val rebuilt = mutableListOf<BuilderPathDecoration>()
    var next = 0
    decorations.forEach { decoration ->
        when {
            decoration !is BuilderPathDecoration.Filter -> rebuilt += decoration
            next < replacements.size -> {
                rebuilt += replacements[next]
                next++
            }
            // More filters were removed than written back; the remaining slots simply close up.
            else -> Unit
        }
    }
    rebuilt += replacements.drop(n = next)
    return copy(decorations = rebuilt)
}

/** Adds, replaces or removes the step's slice, keeping it after the filters already written. */
fun BuilderPathStep.withSlice(slice: BuilderPathDecoration.Slice?): BuilderPathStep {
    val withoutSlice = decorations.filter { it !is BuilderPathDecoration.Slice }
    if (slice == null) {
        return copy(decorations = withoutSlice)
    }
    return copy(decorations = withoutSlice + slice)
}

/**
 * Adds, replaces or removes the step's ordering, keeping it **before** any slice already written.
 *
 * That is what the author almost always means: `take(sortBy(orders, "total", desc), 3)` is the three
 * largest orders, where slicing first would put an arbitrary three in order.
 */
fun BuilderPathStep.withSort(sort: BuilderPathDecoration.Sort?): BuilderPathStep {
    val withoutSort = decorations.filter { it !is BuilderPathDecoration.Sort }
    if (sort == null) {
        return copy(decorations = withoutSort)
    }
    val slicePosition = withoutSort.indexOfFirst { it is BuilderPathDecoration.Slice }
    if (slicePosition < 0) {
        return copy(decorations = withoutSort + sort)
    }
    return copy(
        decorations = withoutSort.take(n = slicePosition) + sort + withoutSort.drop(n = slicePosition)
    )
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
