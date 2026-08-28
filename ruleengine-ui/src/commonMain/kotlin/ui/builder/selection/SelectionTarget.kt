package ui.builder.selection

import ui.builder.model.BuilderExtraction
import ui.builder.model.BuilderFilter
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.mutable.MutableBuilderAction
import ui.builder.model.mutable.MutableBuilderComparison
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.mutable.MutableBuilderVariable
import ui.builder.model.mutable.MutableConditionNode

/**
 * What a selection resolved to, and — for the immutable parts of the tree — how to write it back.
 *
 * The `write` lambdas are the point. A comparison's operands, a path's segments and a filter's sides
 * are immutable values nested inside one another, held in a single Compose state slot on the row (see
 * [MutableBuilderComparison]). Reading a nested operand is a walk; *editing* one means rebuilding
 * every value between it and that slot. Returning the setter alongside the value is what lets the
 * inspector edit six levels down without a chain of `onChanged` callbacks threaded through six
 * composables — which is what the old inline editors did, and why depth had to be laid out rather
 * than navigated.
 *
 * Not in a `model` package, and not `data class`es: a target carries behaviour, and equality over a
 * lambda is meaningless.
 */
sealed interface SelectionTarget {

    /** A simple condition row. Mutable in place, so it needs no setter. */
    class Condition(val condition: MutableBuilderCondition) : SelectionTarget

    /** A comparison row, whose two sides may be computed. Mutable in place. */
    class Comparison(val comparison: MutableBuilderComparison) : SelectionTarget

    /** A parenthesised group. Mutable in place. */
    class Group(val group: MutableConditionNode.Group) : SelectionTarget

    /** An action row of one branch. Mutable in place. */
    class Action(val action: MutableBuilderAction) : SelectionTarget

    /** A `set` or `add` row of one branch. Mutable in place. */
    class Assignment(val assignment: MutableBuilderVariable) : SelectionTarget

    /**
     * One side of a comparison, an argument, a term, or an assignment's value.
     *
     * [scope] is the catalog this operand's names resolve against — the element's inside a `where`,
     * the document's everywhere else.
     */
    class Operand(
        val operand: BuilderOperand,
        val write: (BuilderOperand) -> Unit,
        val scope: ResolutionScope,
    ) : SelectionTarget

    /**
     * One segment of a field or aggregate path, with its filters, ordering and slice.
     *
     * [scope] carries the segments before this one, without which the editor would resolve a fourth
     * segment against the schema's top-level fields.
     */
    class Segment(
        val segment: BuilderPathStep,
        val write: (BuilderPathStep) -> Unit,
        val scope: ResolutionScope,
    ) : SelectionTarget

    /** One `where` restriction of a path segment, scoped to the element it restricts. */
    class Filter(
        val filter: BuilderFilter,
        val write: (BuilderFilter) -> Unit,
        val scope: ResolutionScope,
    ) : SelectionTarget

    /**
     * The `extract … regex(…)` clause of an action.
     *
     * Always document-scoped — an action runs on the record, never on a collection element — but it
     * carries its [scope] like every other leaf so the editor has one place to read the catalog from.
     */
    class Extraction(
        val extraction: BuilderExtraction,
        val write: (BuilderExtraction) -> Unit,
        val scope: ResolutionScope,
    ) : SelectionTarget
}
