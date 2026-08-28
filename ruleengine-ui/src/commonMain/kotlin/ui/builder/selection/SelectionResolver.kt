package ui.builder.selection

import ruleengine.core.domain.dto.RuleBranch
import ui.builder.OperandRules
import ui.builder.model.BuilderFilter
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.filters
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableBuilderAction
import ui.builder.model.mutable.MutableBuilderVariable
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.pathOrNull
import ui.builder.model.selection.SelectionStep
import ui.builder.model.withFilters
import ui.builder.model.withPath
import ui.util.replaceAt

/**
 * Resolves a selection — an anchor id plus a list of [SelectionStep]s — against a rule's editor state.
 *
 * Deliberately takes the anchor in pieces rather than an `InspectorItem`: that type lives in
 * `ui.workbench.model`, and the builder must not depend on the workbench. The caller unpacks it.
 *
 * Every walk can fail, and returning null is the correct answer rather than an exception. A selection
 * outlives what it points at: an argument is removed while its editor is open, a path segment is
 * repointed and its tail dropped, a row is deleted from another canvas. The inspector renders its
 * placeholder for null, which is what it already does for a rule that was edited away.
 */
object SelectionResolver {

    /** Resolves a selection anchored on the condition row [conditionId]. */
    fun resolveCondition(
        state: BuilderEditorState,
        conditionId: String,
        steps: List<SelectionStep>,
        catalog: BuilderCatalog = BuilderCatalog.Empty,
    ): SelectionTarget? {
        val node = findNode(nodes = state.conditionNodes, id = conditionId) ?: return null
        if (steps.isEmpty()) {
            return anchorTarget(node = node)
        }
        // Only a comparison has operands to walk into; a simple condition's field, operator and value
        // are edited on the row itself.
        val comparison = (node as? MutableConditionNode.ComparisonLeaf)?.inner ?: return null
        return when (steps.first()) {
            SelectionStep.Left -> walkOperand(
                operand = comparison.left,
                write = { value -> comparison.left = value },
                steps = steps.drop(n = 1),
                scope = ResolutionScope(catalog = catalog),
            )

            SelectionStep.Right -> walkOperand(
                operand = comparison.right,
                write = { value -> comparison.right = value },
                steps = steps.drop(n = 1),
                scope = ResolutionScope(catalog = catalog),
            )

            else -> null
        }
    }

    /** Resolves a selection anchored on an action or assignment row of [branch]. */
    fun resolveStatement(
        state: BuilderEditorState,
        branch: RuleBranch,
        statementId: String,
        steps: List<SelectionStep>,
        catalog: BuilderCatalog = BuilderCatalog.Empty,
    ): SelectionTarget? {
        val action = state.actionsOf(branch = branch).firstOrNull { it.id == statementId }
        if (action != null) {
            return resolveAction(action = action, steps = steps, catalog = catalog)
        }
        val assignment = state.variablesOf(branch = branch).firstOrNull { it.id == statementId }
        if (assignment != null) {
            return resolveAssignment(assignment = assignment, steps = steps, catalog = catalog)
        }
        return null
    }

    /** An action row: itself, or its `extract` clause. It holds no operands of its own. */
    private fun resolveAction(
        action: MutableBuilderAction,
        steps: List<SelectionStep>,
        catalog: BuilderCatalog,
    ): SelectionTarget? {
        if (steps.isEmpty()) {
            return SelectionTarget.Action(action = action)
        }
        val extraction = action.extraction
        if (steps.size != 1 || steps.first() != SelectionStep.Extraction || extraction == null) {
            return null
        }
        return SelectionTarget.Extraction(
            extraction = extraction,
            write = { value -> action.extraction = value },
            scope = ResolutionScope(catalog = catalog),
        )
    }

    /** A `set` or `add` row: itself, or a walk into the operand it assigns. */
    private fun resolveAssignment(
        assignment: MutableBuilderVariable,
        steps: List<SelectionStep>,
        catalog: BuilderCatalog,
    ): SelectionTarget? {
        if (steps.isEmpty()) {
            return SelectionTarget.Assignment(assignment = assignment)
        }
        if (steps.first() != SelectionStep.Value) {
            return null
        }
        return walkOperand(
            operand = assignment.expression,
            write = { value -> assignment.expression = value },
            steps = steps.drop(n = 1),
            scope = ResolutionScope(catalog = catalog),
        )
    }

    /**
     * The row a selection is anchored on, wherever it sits in the tree.
     *
     * Recursive because a row inside a group is still a row: ids are unique within a rule, so the
     * first match at any depth is the one meant.
     */
    fun findNode(nodes: List<MutableConditionNode>, id: String): MutableConditionNode? {
        for (node in nodes) {
            if (node.id == id) {
                return node
            }
            if (node is MutableConditionNode.Group) {
                val found = findNode(nodes = node.nodes, id = id)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun anchorTarget(node: MutableConditionNode): SelectionTarget {
        return when (node) {
            is MutableConditionNode.Leaf -> SelectionTarget.Condition(condition = node.inner)
            is MutableConditionNode.ComparisonLeaf -> SelectionTarget.Comparison(comparison = node.inner)
            is MutableConditionNode.Group -> SelectionTarget.Group(group = node)
        }
    }

    /**
     * Walks into [operand], composing a setter that rebuilds the chain above it.
     *
     * Each recursion wraps the caller's [write] in one that copies the current operand with the child
     * replaced — so the setter handed back at the bottom writes all the way up to the row's state slot
     * in a single assignment.
     */
    private fun walkOperand(
        operand: BuilderOperand,
        write: (BuilderOperand) -> Unit,
        steps: List<SelectionStep>,
        scope: ResolutionScope,
    ): SelectionTarget? {
        if (steps.isEmpty()) {
            return SelectionTarget.Operand(operand = operand, write = write, scope = scope.atRoot())
        }
        val step = steps.first()
        val rest = steps.drop(n = 1)

        if (step is SelectionStep.Argument && operand is BuilderOperand.Call) {
            val child = operand.args.getOrNull(index = step.index) ?: return null
            return walkOperand(
                operand = child,
                write = { value ->
                    write(operand.copy(args = operand.args.replaceAt(index = step.index, value = value)))
                },
                steps = rest,
                // An argument starts a path of its own, so it inherits the catalog but not the prefix.
                scope = scope.atRoot(),
            )
        }

        if (step is SelectionStep.Term && operand is BuilderOperand.Calc) {
            val term = operand.terms.getOrNull(index = step.index) ?: return null
            return walkOperand(
                operand = term.operand,
                write = { value ->
                    val terms = operand.terms.replaceAt(
                        index = step.index,
                        value = term.copy(operand = value),
                    )
                    write(operand.copy(terms = terms))
                },
                steps = rest,
                scope = scope.atRoot(),
            )
        }

        if (step is SelectionStep.Segment) {
            val path = operand.pathOrNull ?: return null
            val segment = path.getOrNull(index = step.index) ?: return null
            return walkSegment(
                segment = segment,
                write = { value ->
                    write(operand.withPath(path = path.replaceAt(index = step.index, value = value)))
                },
                steps = rest,
                // The segments in front of this one are what make it the third level rather than the
                // first, which is the whole difference between a resolvable path and an undeclared one.
                scope = scope.copy(prefix = path.take(n = step.index)),
            )
        }

        return null
    }

    /** From a path segment, the only way down is into one of its `where` restrictions. */
    private fun walkSegment(
        segment: BuilderPathStep,
        write: (BuilderPathStep) -> Unit,
        steps: List<SelectionStep>,
        scope: ResolutionScope,
    ): SelectionTarget? {
        if (steps.isEmpty()) {
            return SelectionTarget.Segment(segment = segment, write = write, scope = scope)
        }
        val step = steps.first()
        if (step !is SelectionStep.Filter) {
            return null
        }
        val filters = segment.filters
        val filter = filters.getOrNull(index = step.index) ?: return null
        return walkFilter(
            filter = filter,
            write = { value ->
                write(segment.withFilters(filters = filters.replaceAt(index = step.index, value = value)))
            },
            steps = steps.drop(n = 1),
            scope = elementScope(segment = segment, scope = scope),
        )
    }

    /**
     * The scope a `where` restriction resolves in: the element's members, with the document behind
     * them.
     *
     * This is the crossing the engine makes in `ValueExpressionCompiler.elementSchema`, and the reason
     * `orders[month in completeMonths]` reads `month` off the element rather than off the record.
     */
    private fun elementScope(segment: BuilderPathStep, scope: ResolutionScope): ResolutionScope {
        val path = scope.prefix + segment
        return ResolutionScope(
            catalog = OperandRules.filterCatalog(
                fields = scope.catalog,
                path = path,
                depth = path.lastIndex,
            ),
        )
    }

    /** A filter is a comparison one level down, so its sides are reached the same way. */
    private fun walkFilter(
        filter: BuilderFilter,
        write: (BuilderFilter) -> Unit,
        steps: List<SelectionStep>,
        scope: ResolutionScope,
    ): SelectionTarget? {
        if (steps.isEmpty()) {
            return SelectionTarget.Filter(filter = filter, write = write, scope = scope)
        }
        return when (steps.first()) {
            SelectionStep.Left -> walkOperand(
                operand = filter.left,
                write = { value -> write(filter.copy(left = value)) },
                steps = steps.drop(n = 1),
                scope = scope,
            )

            SelectionStep.Right -> walkOperand(
                operand = filter.right,
                write = { value -> write(filter.copy(right = value)) },
                steps = steps.drop(n = 1),
                scope = scope,
            )

            else -> null
        }
    }
}
