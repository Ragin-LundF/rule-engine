package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.ast.AssignmentKindAst
import ui.builder.OperatorOptions
import ui.builder.model.BuilderAction
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderLockKind
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderRule
import ui.builder.model.BuilderVariable

/** Converts a [MutableConditionNode] tree to an immutable [BuilderConditionNode] tree. */
fun MutableConditionNode.toImmutable(): BuilderConditionNode = when (this) {
    is MutableConditionNode.Leaf -> inner.toImmutable()
    is MutableConditionNode.ComparisonLeaf -> inner.toImmutable()
    is MutableConditionNode.Group -> BuilderConditionNode.Group(
        nodeId = id,
        nodes = nodes.map { it.toImmutable() },
        negated = negated,
        joinToPrevious = joinToPrevious,
    )
}

@Suppress("LongParameterList")
class BuilderEditorState private constructor(
    val ruleId: String,
    description: String,
    val conditionNodes: SnapshotStateList<MutableConditionNode>,
    val actions: SnapshotStateList<MutableBuilderAction>,
    val variables: SnapshotStateList<MutableBuilderVariable>,
    /** Actions of the `else` block. Empty when the rule declares no false branch. */
    val elseActions: SnapshotStateList<MutableBuilderAction>,
    /** `set` rows of the `else` block. */
    val elseVariables: SnapshotStateList<MutableBuilderVariable>,
    /** Actions of the `not_exists` block. Empty when the rule declares no missing-data branch. */
    val notExistsActions: SnapshotStateList<MutableBuilderAction>,
    /** `set` rows of the `not_exists` block. */
    val notExistsVariables: SnapshotStateList<MutableBuilderVariable>,
    stopOnThen: Boolean,
    stopOnElse: Boolean,
    stopOnNotExists: Boolean,
    val isLocked: Boolean,
    val lockReason: String,
    val lockKind: BuilderLockKind = BuilderLockKind.NONE,
) {
    /** The rule's optional `description` clause. Editable, unlike [ruleId], which is renamed separately. */
    var description by mutableStateOf(value = description)

    /**
     * Whether the THEN branch ends the run.
     *
     * A flag rather than a row in [actions], which is what keeps `stop` pinned to the end of the branch:
     * there is no position for it to hold, so adding another action afterwards cannot push output below
     * it, and the generated DSL always writes it last.
     */
    var stopOnThen by mutableStateOf(value = stopOnThen)

    /** Whether the ELSE branch ends the run. The [stopOnThen] counterpart for the false branch. */
    var stopOnElse by mutableStateOf(value = stopOnElse)

    /** Whether the NOT_EXISTS branch ends the run. The [stopOnThen] counterpart for that branch. */
    var stopOnNotExists by mutableStateOf(value = stopOnNotExists)

    private var nextConditionId = conditionNodes.size + 1

    // One counter per kind across every branch: a row id has to be unique within the rule, because
    // the views key on it and a duplicate would make a removal in one branch hit another.
    private var nextActionId = actions.size + elseActions.size + notExistsActions.size + 1
    private var nextVariableId = variables.size + elseVariables.size + notExistsVariables.size + 1

    /** The action rows of [branch], so a caller can drive any branch through the same code. */
    fun actionsOf(branch: RuleBranch): SnapshotStateList<MutableBuilderAction> {
        return when (branch) {
            RuleBranch.THEN -> actions
            RuleBranch.ELSE -> elseActions
            RuleBranch.NOT_EXISTS -> notExistsActions
        }
    }

    /** The `set` and `add` rows of [branch]. */
    fun variablesOf(branch: RuleBranch): SnapshotStateList<MutableBuilderVariable> {
        return when (branch) {
            RuleBranch.THEN -> variables
            RuleBranch.ELSE -> elseVariables
            RuleBranch.NOT_EXISTS -> notExistsVariables
        }
    }

    /** Whether [branch] ends the run. */
    fun stopOf(branch: RuleBranch): Boolean {
        return when (branch) {
            RuleBranch.THEN -> stopOnThen
            RuleBranch.ELSE -> stopOnElse
            RuleBranch.NOT_EXISTS -> stopOnNotExists
        }
    }

    /** Adds or removes the `stop` on [branch]. */
    fun setStop(branch: RuleBranch, stop: Boolean) {
        when (branch) {
            RuleBranch.THEN -> stopOnThen = stop
            RuleBranch.ELSE -> stopOnElse = stop
            RuleBranch.NOT_EXISTS -> stopOnNotExists = stop
        }
    }

    /**
     * True when the rule declares an `else` block that produces something.
     *
     * A bare `stop` counts: "halt the run when this condition does not hold" is a real branch, and the
     * DSL can express it.
     */
    val hasElseBranch: Boolean
        get() = elseActions.isNotEmpty() || elseVariables.isNotEmpty() || stopOnElse

    /** True when the rule declares a `not_exists` block that produces something. */
    val hasNotExistsBranch: Boolean
        get() = notExistsActions.isNotEmpty() || notExistsVariables.isNotEmpty() || stopOnNotExists

    /** True when [branch] is declared at all — the `then` block always is. */
    fun hasBranch(branch: RuleBranch): Boolean {
        return when (branch) {
            RuleBranch.THEN -> true
            RuleBranch.ELSE -> hasElseBranch
            RuleBranch.NOT_EXISTS -> hasNotExistsBranch
        }
    }

    companion object {
        fun fromBuilderRule(rule: BuilderRule): BuilderEditorState = when (rule) {
            is BuilderRule.Supported -> BuilderEditorState(
                ruleId = rule.id,
                description = rule.description,
                conditionNodes = rule.conditionNodes.map { it.toMutable() }.toMutableStateList(),
                actions = rule.actions.toMutableActions(),
                variables = rule.variables.toMutableVariables(),
                elseActions = rule.elseActions.toMutableActions(),
                elseVariables = rule.elseVariables.toMutableVariables(),
                notExistsActions = rule.notExistsActions.toMutableActions(),
                notExistsVariables = rule.notExistsVariables.toMutableVariables(),
                stopOnThen = rule.stopOnThen,
                stopOnElse = rule.stopOnElse,
                stopOnNotExists = rule.stopOnNotExists,
                isLocked = false,
                lockReason = "",
                lockKind = BuilderLockKind.NONE,
            )

            is BuilderRule.Unsupported -> BuilderEditorState(
                ruleId = rule.id,
                description = "",
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                variables = mutableStateListOf(),
                elseActions = mutableStateListOf(),
                elseVariables = mutableStateListOf(),
                notExistsActions = mutableStateListOf(),
                notExistsVariables = mutableStateListOf(),
                stopOnThen = false,
                stopOnElse = false,
                stopOnNotExists = false,
                isLocked = true,
                lockReason = rule.reason,
                lockKind = BuilderLockKind.UNSUPPORTED_SYNTAX,
            )

            BuilderRule.None -> BuilderEditorState(
                ruleId = "",
                description = "",
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                variables = mutableStateListOf(),
                elseActions = mutableStateListOf(),
                elseVariables = mutableStateListOf(),
                notExistsActions = mutableStateListOf(),
                notExistsVariables = mutableStateListOf(),
                stopOnThen = false,
                stopOnElse = false,
                stopOnNotExists = false,
                isLocked = true,
                lockReason = "No rule selected.",
                lockKind = BuilderLockKind.NO_RULE_SELECTED,
            )
        }

        private fun List<BuilderAction>.toMutableActions(): SnapshotStateList<MutableBuilderAction> {
            return map { action ->
                MutableBuilderAction(
                    id = action.id,
                    name = action.name,
                    arguments = action.arguments,
                    extraction = action.extraction,
                )
            }.toMutableStateList()
        }

        private fun List<BuilderVariable>.toMutableVariables(): SnapshotStateList<MutableBuilderVariable> {
            return map { variable ->
                MutableBuilderVariable(
                    id = variable.id,
                    name = variable.name,
                    expression = variable.expression,
                    kind = variable.kind,
                )
            }.toMutableStateList()
        }

    }

    /**
     * Removes the group with the given [id] and inserts its children at the
     * group's original position. The first child inherits the group's joinToPrevious.
     * Does nothing if no group with that id exists.
     */
    fun ungroup(id: String) {
        val groupIndex = conditionNodes.indexOfFirst { it.id == id && it is MutableConditionNode.Group }
        if (groupIndex < 0) return
        val group = conditionNodes[groupIndex] as MutableConditionNode.Group
        if (group.nodes.isNotEmpty()) {
            group.nodes.first().joinToPrevious = group.joinToPrevious
        }
        conditionNodes.removeAt(groupIndex)
        conditionNodes.addAll(index = groupIndex, elements = group.nodes.toList())
    }

    /**
     * Groups a set of condition nodes, identified by their IDs, into a single group node.
     * If the set contains fewer than two valid condition nodes, no action is taken.
     *
     * @param ids A set of IDs representing the condition nodes to be grouped. Only IDs that can
     *            be matched to existing condition nodes are considered.
     */
    fun groupConditions(ids: Set<String>) {
        if (ids.size < 2) return
        val indices = ids.mapNotNull { id ->
            conditionNodes.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }.sorted()
        if (indices.size < 2) return

        val nodesToWrap = indices.map { conditionNodes[it] }
        val groupJoin = nodesToWrap.first().joinToPrevious
        val group = MutableConditionNode.Group(
            id = "grp-${nextConditionId++}",
            nodes = nodesToWrap,
            joinToPrevious = groupJoin,
        )

        // Remove from back to front so indices stay valid
        indices.sortedDescending().forEach { conditionNodes.removeAt(it) }
        // Insert the group at the position of the first wrapped node
        conditionNodes.add(index = indices.first(), element = group)
    }

    /**
     * Wraps the single row [id] in a new group, in place.
     *
     * The keyboard-and-click counterpart to [groupConditions], which wraps a multi-row selection. A
     * one-row group is a valid starting point — the author adds the second row into it — and the
     * generated `( … )` parses either way.
     */
    fun wrapInGroup(id: String): Boolean {
        val holder = containerOf(nodes = conditionNodes, id = id) ?: return false
        val index = holder.indexOfFirst { node -> node.id == id }
        if (index < 0) {
            return false
        }
        val node = holder[index]
        val group = MutableConditionNode.Group(
            id = "grp-${nextConditionId++}",
            nodes = listOf(node),
            joinToPrevious = node.joinToPrevious,
        )
        // The wrapped row's own join now belongs to the group; leaving it on both would emit it twice.
        node.joinToPrevious = ""
        holder[index] = group
        return true
    }

    /** The list that directly holds [id], at any depth — what an in-place replacement needs. */
    private fun containerOf(
        nodes: SnapshotStateList<MutableConditionNode>,
        id: String,
    ): SnapshotStateList<MutableConditionNode>? {
        if (nodes.any { node -> node.id == id }) {
            return nodes
        }
        for (group in nodes.filterIsInstance<MutableConditionNode.Group>()) {
            val found = containerOf(nodes = group.nodes, id = id)
            if (found != null) {
                return found
            }
        }
        return null
    }

    /**
     * Adds a new empty condition inside the group with the given [groupId].
     * If the group doesn't exist, does nothing.
     */
    fun addConditionInside(
        groupId: String,
        defaultField: String = "",
        defaultOperator: String = OperatorOptions.EQUALS
    ): MutableBuilderCondition? {
        val group = findGroupById(groupId) ?: return null
        val condition = MutableBuilderCondition(
            id = "cond-${nextConditionId++}",
            field = defaultField,
            operator = defaultOperator,
            value = "",
            joinToPrevious = group.nodes.lastOrNull()?.let { "and" } ?: "",
        )
        group.nodes.add(MutableConditionNode.Leaf(condition))
        return condition
    }

    private fun findGroupById(id: String): MutableConditionNode.Group? {
        for (node in conditionNodes) {
            if (node is MutableConditionNode.Group && node.id == id) return node
        }
        return null
    }

    /** Adds a new empty condition after the existing ones, at the top level. */
    fun addCondition(
        defaultField: String = "",
        defaultOperator: String = OperatorOptions.EQUALS,
    ): MutableBuilderCondition {
        val condition = MutableBuilderCondition(
            id = "cond-${nextConditionId++}",
            field = defaultField,
            operator = defaultOperator,
            value = "",
            joinToPrevious = conditionNodes.lastOrNull()?.let { "and" } ?: "",
        )
        conditionNodes.add(MutableConditionNode.Leaf(condition))
        return condition
    }

    /**
     * Removes the leaf or group with the given [id], wherever it sits in the tree.
     *
     * Recursive, like [replaceNode] — a row inside a group is still a row with an × on it, and
     * removing only from the top level made that × silently do nothing.
     */
    fun removeCondition(id: String) {
        removeIn(nodes = conditionNodes, id = id)
    }

    /**
     * Why [id] cannot be removed, or null when it can.
     *
     * A rule must keep at least one condition and at least one outcome in `then`: neither an empty
     * `when` nor an empty `then` parses, and the Builder regenerates the whole rule text on every
     * edit, so a gesture that empties either writes a broken rule to the file.
     *
     * A reason rather than a silent refusal, because a delete button that does nothing reads as
     * broken. The caller shows it — see the `onMessage` channel on the Builder views.
     */
    fun blockedRemoval(id: String): String? {
        if (findAnyNode(nodes = conditionNodes, id = id) != null && countLeafConditions() <= 1) {
            return "A rule needs at least one condition — edit this one instead of removing it."
        }
        val isLastThenAction = actions.size <= 1 &&
            actions.any { action -> action.id == id } &&
            variables.isEmpty() &&
            !stopOnThen
        if (isLastThenAction) {
            return "A rule needs at least one outcome in THEN — add another before removing this one."
        }
        val isLastThenVariable = variables.size <= 1 &&
            variables.any { variable -> variable.id == id } &&
            actions.isEmpty() &&
            !stopOnThen
        if (isLastThenVariable) {
            return "A rule needs at least one outcome in THEN — add another before removing this one."
        }
        return null
    }

    /** Leaf conditions across the whole tree, groups included. */
    fun countLeafConditions(): Int {
        return countLeaves(nodes = conditionNodes)
    }

    private fun countLeaves(nodes: List<MutableConditionNode>): Int {
        return nodes.sumOf { node ->
            when (node) {
                is MutableConditionNode.Leaf -> 1
                is MutableConditionNode.ComparisonLeaf -> 1
                is MutableConditionNode.Group -> countLeaves(nodes = node.nodes)
            }
        }
    }

    /** Any node with [id], group or leaf, at any depth. */
    private fun findAnyNode(nodes: List<MutableConditionNode>, id: String): MutableConditionNode? {
        for (node in nodes) {
            if (node.id == id) {
                return node
            }
            if (node is MutableConditionNode.Group) {
                val found = findAnyNode(nodes = node.nodes, id = id)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    /**
     * Why moving the statement [id] out of [from] is refused, or null when it is allowed.
     *
     * Dragging the only `then` outcome into another lane empties `then`, which is the same broken
     * rule a delete would produce.
     */
    fun blockedMove(id: String, from: RuleBranch): String? {
        if (from != RuleBranch.THEN) {
            return null
        }
        return blockedRemoval(id = id)
    }

    private fun removeIn(nodes: SnapshotStateList<MutableConditionNode>, id: String): Boolean {
        if (nodes.removeAll { it.id == id }) {
            return true
        }
        for (group in nodes.filterIsInstance<MutableConditionNode.Group>()) {
            if (!removeIn(nodes = group.nodes, id = id)) {
                continue
            }
            // A group that has lost its last child renders as `()`, which does not parse, so the
            // empty parentheses go with it.
            if (group.nodes.isEmpty()) {
                nodes.remove(element = group)
            }
            return true
        }
        return false
    }

    /**
     * Replaces the node with the given [id] anywhere in the tree, preserving its position.
     * Returns true when a node was replaced.
     */
    fun replaceNode(id: String, replacement: MutableConditionNode): Boolean =
        replaceIn(nodes = conditionNodes, id = id, replacement = replacement)

    private fun replaceIn(
        nodes: SnapshotStateList<MutableConditionNode>,
        id: String,
        replacement: MutableConditionNode,
    ): Boolean {
        val index = nodes.indexOfFirst { it.id == id }
        if (index >= 0) {
            replacement.joinToPrevious = nodes[index].joinToPrevious
            nodes[index] = replacement
            return true
        }
        return nodes.filterIsInstance<MutableConditionNode.Group>().any { group ->
            replaceIn(nodes = group.nodes, id = id, replacement = replacement)
        }
    }

    /** Adds a new comparison row after the existing ones, at the top level. */
    fun addComparison(
        left: BuilderOperand,
        operator: String,
        right: BuilderOperand,
    ): MutableBuilderComparison {
        val comparison = MutableBuilderComparison(
            id = "cmp-${nextConditionId++}",
            left = left,
            operator = operator,
            right = right,
            joinToPrevious = conditionNodes.lastOrNull()?.let { "and" } ?: "",
        )
        conditionNodes.add(MutableConditionNode.ComparisonLeaf(inner = comparison))
        return comparison
    }

    /** Adds a new empty action to [branch], with the given number of default arguments. */
    fun addAction(
        defaultName: String = "",
        defaultArgCount: Int = 0,
        branch: RuleBranch = RuleBranch.THEN,
    ): MutableBuilderAction {
        val action = MutableBuilderAction(
            id = "act-${nextActionId++}",
            name = defaultName,
            arguments = List(defaultArgCount) { "" }.toMutableList(),
        )
        actionsOf(branch = branch).add(action)
        return action
    }

    /**
     * Removes the action with the given [id] from whichever branch holds it.
     *
     * Row ids are unique across the rule, so the caller does not have to say which branch it means —
     * and a view that guessed wrong would silently delete nothing.
     */
    fun removeAction(id: String) {
        actions.removeAll { it.id == id }
        elseActions.removeAll { it.id == id }
        notExistsActions.removeAll { it.id == id }
    }

    /**
     * Adds a new assignment row to [branch], after the existing ones.
     *
     * The default expression is a blank literal rather than a field reference: an assignment is
     * usually written to hold something the author types, and an empty value box is the one starting
     * point that is wrong for no one. [kind] decides whether the row reads as a `set` or an `add`.
     */
    fun addVariable(
        defaultName: String = "",
        branch: RuleBranch = RuleBranch.THEN,
        kind: AssignmentKindAst = AssignmentKindAst.SET,
    ): MutableBuilderVariable {
        val variable = MutableBuilderVariable(
            id = "var-${nextVariableId++}",
            name = defaultName,
            expression = BuilderOperand.Literal(text = "", numeric = false),
            kind = kind,
        )
        variablesOf(branch = branch).add(variable)
        return variable
    }

    /** Removes the assignment row with the given [id] from whichever branch holds it. */
    fun removeVariable(id: String) {
        variables.removeAll { it.id == id }
        elseVariables.removeAll { it.id == id }
        notExistsVariables.removeAll { it.id == id }
    }
}
