package ui.builder.model.mutable

import androidx.compose.runtime.snapshots.SnapshotStateList
import ruleengine.core.domain.dto.RuleBranch
import ui.builder.model.BuilderConditionNode

/**
 * Moving things around inside a rule: a condition into a group, a statement between branches.
 *
 * Extensions rather than members of [BuilderEditorState], and the reason is worth stating: these three
 * are the *board's* vocabulary. Every other mutation on that class is shared by both canvases, while
 * these exist because a thing can be dragged. Keeping them here says which is which, and keeps the
 * class from accumulating a method per gesture.
 *
 * All three refuse rather than corrupt. A move is two operations — detach, then insert — and a rule left
 * between them is a rule the generator would write to the file, so each one checks everything it needs
 * before it removes anything.
 */

/**
 * The id of the group holding [id], or null when it sits at the top level.
 *
 * Public because the board needs it to refuse a group dropped inside itself, which is a question
 * about ancestry and has no other answer available: the tree has no parent pointers, so walking
 * down from the root is the only way to find a node's container.
 */
fun BuilderEditorState.groupIdContaining(id: String): String? {
    return groupIdContainingIn(nodes = conditionNodes, id = id)
}

private fun groupIdContainingIn(
    nodes: SnapshotStateList<MutableConditionNode>,
    id: String,
): String? {
    for (group in nodes.filterIsInstance<MutableConditionNode.Group>()) {
        if (group.nodes.any { node -> node.id == id }) {
            return group.id
        }
        groupIdContainingIn(nodes = group.nodes, id = id)?.let { found -> return found }
    }
    return null
}

/**
 * Moves the condition [id] into the group [groupId], keeping it as one node.
 *
 * Returns false and changes nothing when either id is missing, or when the move would put a group
 * inside itself — the caller checks that too, but a mutation that can corrupt the tree must not rely
 * on being called correctly.
 *
 * The join is reset to `and` on arrival, because the join a row had at the top level was a join to a
 * *different* neighbour. Carrying an `or` across would silently change what the group means.
 */
fun BuilderEditorState.moveConditionInto(id: String, groupId: String): Boolean {
    if (id == groupId) {
        return false
    }
    val target = findGroup(nodes = conditionNodes, id = groupId) ?: return false
    val moved = detach(nodes = conditionNodes, id = id) ?: return false

    if (moved is MutableConditionNode.Group && findGroup(nodes = moved.nodes, id = groupId) != null) {
        // The group came out of the tree already, so it has to go back rather than be dropped.
        conditionNodes.add(element = moved)
        return false
    }

    moved.joinToPrevious = if (target.nodes.isEmpty()) "" else "and"
    target.nodes.add(element = moved)
    return true
}

/**
 * Moves the statement [id] from [from] to [to], keeping its kind and its contents.
 *
 * Returns false when the statement is not in [from], or when [blockedMove] refuses it — emptying a
 * `then` block produces a rule that does not parse, and the refusal is the same whether the gesture
 * was a drag or anything else.
 */
fun BuilderEditorState.moveStatement(id: String, from: RuleBranch, to: RuleBranch): Boolean {
    if (from == to || blockedMove(id = id, from = from) != null) {
        return false
    }

    val action = actionsOf(branch = from).firstOrNull { candidate -> candidate.id == id }
    if (action != null) {
        actionsOf(branch = from).remove(element = action)
        actionsOf(branch = to).add(element = action)
        return true
    }

    val variable = variablesOf(branch = from).firstOrNull { candidate -> candidate.id == id }
    if (variable != null) {
        variablesOf(branch = from).remove(element = variable)
        variablesOf(branch = to).add(element = variable)
        return true
    }

    return false
}

/**
 * Removes the node [id] from wherever it is and returns it, or null when it is not there.
 *
 * Unlike [removeIn] this keeps the node, because a move needs it back. It also leaves an emptied
 * group in place rather than deleting it: the node is about to be re-inserted, and a move that
 * removed a bracket as a side effect would change the rule's precedence rather than its layout.
 */
private fun detach(
    nodes: SnapshotStateList<MutableConditionNode>,
    id: String,
): MutableConditionNode? {
    val index = nodes.indexOfFirst { node -> node.id == id }
    if (index >= 0) {
        return nodes.removeAt(index = index)
    }
    for (group in nodes.filterIsInstance<MutableConditionNode.Group>()) {
        detach(nodes = group.nodes, id = id)?.let { found -> return found }
    }
    return null
}

private fun findGroup(
    nodes: SnapshotStateList<MutableConditionNode>,
    id: String,
): MutableConditionNode.Group? {
    for (group in nodes.filterIsInstance<MutableConditionNode.Group>()) {
        if (group.id == id) {
            return group
        }
        findGroup(nodes = group.nodes, id = id)?.let { found -> return found }
    }
    return null
}

/**
 * Replaces the row [id] with the node the formula bar parsed, keeping the row's own identity.
 *
 * The parsed node arrives with a fresh id and no join, because it came out of a synthetic one-condition
 * rule that had neither. Both are carried over from the row being replaced, and that is the whole
 * subtlety here: taking the new node's id would break every selection pointing at this row, and taking
 * its blank join would silently turn an `or` into an `and` — a different rule that still parses.
 *
 * The negation is *not* carried over. `not` is spelled in the text the author typed, so the parse is
 * already the authority on it; carrying the old flag as well would apply it twice.
 */
fun BuilderEditorState.replaceNodeFromFormula(id: String, parsed: BuilderConditionNode): Boolean {
    val existing = nodeById(nodes = conditionNodes, id = id) ?: return false
    val adopted = parsed.withIdentityOf(nodeId = existing.id, joinToPrevious = existing.joinToPrevious)

    return replaceNode(id = id, replacement = adopted.toMutable())
}

/**
 * The same node under the replaced row's id and join.
 *
 * Adjusted before the conversion rather than after it, because a [MutableConditionNode]'s id is a `val`
 * — deliberately, since a row's identity is what every selection points at and must not be reassignable.
 */
private fun BuilderConditionNode.withIdentityOf(
    nodeId: String,
    joinToPrevious: String,
): BuilderConditionNode = when (this) {
    is BuilderConditionNode.Condition -> copy(nodeId = nodeId, joinToPrevious = joinToPrevious)
    is BuilderConditionNode.Comparison -> copy(nodeId = nodeId, joinToPrevious = joinToPrevious)
    is BuilderConditionNode.Group -> copy(nodeId = nodeId, joinToPrevious = joinToPrevious)
}

private fun nodeById(
    nodes: SnapshotStateList<MutableConditionNode>,
    id: String,
): MutableConditionNode? {
    nodes.firstOrNull { node -> node.id == id }?.let { found -> return found }
    for (group in nodes.filterIsInstance<MutableConditionNode.Group>()) {
        nodeById(nodes = group.nodes, id = id)?.let { found -> return found }
    }
    return null
}
