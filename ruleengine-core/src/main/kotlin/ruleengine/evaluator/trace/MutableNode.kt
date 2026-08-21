package ruleengine.evaluator.trace

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.NodeType

/**
 * A decision-tree node while it is still being recorded.
 *
 * Not a DTO despite its shape: [RecordingTraceCollector] pushes one of these on entering an
 * expression and fills in [verdict] and [elapsedMs] on the way out, so it is mutable by design and
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
    var verdict: ConditionVerdict? = null,
    var branch: RuleBranch? = null,
    var startNs: Long? = null,
    var elapsedMs: Long? = null,
    val children: MutableList<MutableNode> = mutableListOf(),
    var ruleId: String? = null
) {

    /**
     * Freezes this subtree into its immutable form.
     *
     * A node whose [verdict] was never set is reported as [ConditionVerdict.FALSE]: an expression that
     * short-circuited before reaching it never evaluated to true, and it was not left undecided either
     * — it was not asked.
     */
    fun toDecisionNode(): DecisionNode {
        val settled = verdict ?: ConditionVerdict.FALSE
        return DecisionNode(
            id = id,
            type = type,
            field = field,
            operator = operator,
            expected = expected,
            actual = actual,
            result = settled.isTrue(),
            verdict = settled,
            branch = branch,
            evaluationTimeMs = elapsedMs,
            ruleId = ruleId,
            children = children.map { child -> child.toDecisionNode() }
        )
    }
}
