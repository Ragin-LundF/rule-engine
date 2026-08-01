package ui.diagrams.model

import ruleengine.dsl.ast.RuleAst

/**
 * The bipartite edges, precomputed once per rule set.
 *
 * Fields are the schema's leaves, so a declared-but-unread field keeps its row. A path a rule reads
 * that is not a schema leaf — a bare collection such as `parcels` in `count(parcels[...])` — is
 * dropped, because it is a step on the way to a value rather than a value.
 */
internal class FlowModel(
    val fields: List<FieldNode>,
    val rules: List<RuleAst>,
    val families: List<String>,
    val rulesByField: Map<String, List<String>>,
    val fieldsByRule: Map<String, List<String>>,
    val familiesByRule: Map<String, List<String>>,
    val valuesByFamily: Map<String, Set<String>>,
) {
    /** The node itself plus everything reachable from it in either direction, or null for no selection. */
    fun connectedTo(nodeId: String?): Set<String>? {
        if (nodeId == null) {
            return null
        }
        val reachedRules = when {
            rules.any { rule -> rule.id == nodeId } -> listOf(nodeId)
            else -> rules.map { rule -> rule.id }.filter { ruleId ->
                nodeId in fieldsByRule[ruleId].orEmpty() || nodeId in familiesByRule[ruleId].orEmpty()
            }
        }
        val lit = mutableSetOf(nodeId)
        reachedRules.forEach { ruleId ->
            lit += ruleId
            lit += fieldsByRule[ruleId].orEmpty()
            lit += familiesByRule[ruleId].orEmpty()
        }
        return lit
    }
}
