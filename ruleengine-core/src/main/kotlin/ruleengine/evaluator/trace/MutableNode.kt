package ruleengine.evaluator.trace

import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.NodeType

/**
 * A decision-tree node while it is still being recorded.
 *
 * Not a DTO despite its shape: [RecordingTraceCollector] pushes one of these on entering an
 * expression and fills in [result] and [elapsedMs] on the way out, so it is mutable by design and
 * lives only for the duration of one evaluation. The immutable [DecisionNode] produced by
 * [toDecisionNode] is what everything downstream sees.
 */
internal data class MutableNode(
    val id: String,
    val type: NodeType,
    val field: String?,
    val operator: String?,
    val expected: Any?,
    val actual: Any? = null,
    var result: Boolean? = null,
    var startNs: Long? = null,
    var elapsedMs: Long? = null,
    val children: MutableList<MutableNode> = mutableListOf(),
    var ruleId: String? = null
) {

    /**
     * Freezes this subtree into its immutable form.
     *
     * A node whose [result] was never set is reported as `false`: an expression that short-circuited
     * before reaching it never evaluated to true.
     */
    fun toDecisionNode(): DecisionNode {
        return DecisionNode(
            id = id,
            type = type,
            field = field,
            operator = operator,
            expected = expected,
            actual = actual,
            result = result ?: false,
            evaluationTimeMs = elapsedMs,
            ruleId = ruleId,
            children = children.map { child -> child.toDecisionNode() }
        )
    }
}
