package ui.tester

/**
 * One node of a rule's recorded decision tree — the UI-side mirror of the core's `DecisionNode`.
 *
 * [TraceRow] flattens a rule's trace to its condition leaves, which is all the results list needs.
 * The trace diagram needs the shape as well: which conditions sat under which `and`, and where
 * evaluation stopped. So the tree is kept whole here and the rows are derived from it, rather than
 * the two being collected separately and drifting apart.
 *
 * [result] is not nullable, because a node that was never evaluated is **absent** from the tree
 * rather than present and undecided: `AndExpression` returns on the first false child without ever
 * calling the collector for the remaining ones. A false [TraceNodeType.AND] node with fewer children
 * than its rule text implies is therefore normal, and it is where evaluation stopped.
 *
 * @param label Condition text for [TraceNodeType.CONDITION], the rule id for [TraceNodeType.RULE],
 *   empty for the logical nodes, whose kind is carried by [type].
 * @param actual The value actually found, when the emitting expression knew it.
 */
data class TraceNode(
    val type: TraceNodeType,
    val label: String,
    val result: Boolean,
    val actual: String? = null,
    val children: List<TraceNode> = emptyList(),
)
