package ui.builder.model.selection

/**
 * One step from a selected row or statement down into the thing being edited.
 *
 * A selection is an anchor — a condition node id, or a statement id in a branch — plus a list of
 * these steps. `when/2`'s left aggregate's first path segment's second `where` filter's right side is
 * `[Left, Segment(0), Filter(1), Right]`, and resolving it is a walk, not a search.
 *
 * This exists so **depth is navigation rather than layout**. The Builder used to express it with a
 * `remember`ed flag per component — `ExpandedSide`, `expandedTerm`, `expandedArg` — which allowed
 * exactly one open panel at a time and stacked those panels under the row, pushing the row being
 * edited off screen. One selection path replaces all of them: the row stays where it is and the
 * editor renders whatever the path points at.
 *
 * Steps are deliberately positional. Operands are immutable values replaced wholesale on edit (see
 * [ui.builder.model.BuilderOperand]), so they carry no ids to key on, and an index is the only stable
 * way to name a term or an argument. The anchor, by contrast, *is* an id, because rows and statements
 * are reordered and an index there would follow the wrong one.
 */
sealed interface SelectionStep {

    /**
     * The left side of a comparison, or of a `where` filter after a [Filter] step.
     *
     * Shared between the two because a filter is a comparison one level down — the same
     * `operand · operator · operand` shape — and the engine resolves both the same way.
     */
    data object Left : SelectionStep

    /** The right side of a comparison, or of a `where` filter after a [Filter] step. */
    data object Right : SelectionStep

    /** The operand a `set` or `add` statement assigns. */
    data object Value : SelectionStep

    /** The `extract … regex(…)` clause of an action, which has no operand of its own. */
    data object Extraction : SelectionStep

    /** The [index]-th argument of a function call. */
    data class Argument(val index: Int) : SelectionStep

    /** The operand of the [index]-th term of an arithmetic chain. */
    data class Term(val index: Int) : SelectionStep

    /** The [index]-th segment of a field path or an aggregate's path. */
    data class Segment(val index: Int) : SelectionStep

    /**
     * The [index]-th `where` filter of the segment reached by the preceding [Segment] step.
     *
     * Always follows a [Segment]: a filter belongs to one level of a path, and which level it
     * restricts is the whole difference between `orders[paid].items.price` and
     * `orders.items[price > 0].price`.
     */
    data class Filter(val index: Int) : SelectionStep
}
