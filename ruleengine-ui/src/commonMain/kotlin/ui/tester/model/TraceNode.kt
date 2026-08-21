package ui.tester.model

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.evaluator.trace.dto.NodeType

/**
 * One node of a rule's recorded decision tree — the UI-side view of the core's `DecisionNode`,
 * flattened to the strings the trace views render. The node *kind* is the engine's own [NodeType];
 * the UI used to restate it as a parallel enum and convert one to the other one-for-one.
 *
 * [TraceRow] flattens a rule's trace to its condition leaves, which is all the results list needs.
 * The trace diagram needs the shape as well: which conditions sat under which `and`, and where
 * evaluation stopped. So the tree is kept whole here and the rows are derived from it, rather than
 * the two being collected separately and drifting apart.
 *
 * [result] is not nullable, because a node that was never evaluated is **absent** from the tree
 * rather than present and undecided: `AndExpression` returns on the first false child without ever
 * calling the collector for the remaining ones. A false [NodeType.AND] node with fewer children
 * than its rule text implies is therefore normal, and it is where evaluation stopped.
 *
 * "Undecided" is a different thing and does have a place here: [verdict] is
 * [ConditionVerdict.UNKNOWN] for a node the record carried no data for. That node *was* evaluated —
 * it just had nothing to compare.
 *
 * @param label Condition text for [NodeType.CONDITION], the rule id for [NodeType.RULE],
 *   empty for the logical nodes, whose kind is carried by [type].
 * @param actual The value actually found, when the emitting expression knew it.
 * @param verdict What the node answered, including [ConditionVerdict.UNKNOWN].
 * @param branch On a [NodeType.RULE] node, the block the verdict selected. Null on every other node.
 */
data class TraceNode(
    val type: NodeType,
    val label: String,
    val result: Boolean,
    val actual: String? = null,
    val verdict: ConditionVerdict = ConditionVerdict.of(value = result),
    val branch: RuleBranch? = null,
    val children: List<TraceNode> = emptyList(),
)
