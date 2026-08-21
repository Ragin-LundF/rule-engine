package ruleengine.evaluator.trace.dto

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.RuleBranch

data class DecisionNode(
    val id: String,
    val type: NodeType,
    val field: String? = null,
    val operator: String? = null,
    val expected: Any? = null,
    /** The value actually found, when the emitting expression knows it. Omitted from JSON when null. */
    val actual: Any? = null,
    /**
     * Whether the node held.
     *
     * Kept alongside [verdict] rather than replaced by it: it is what every existing reader of a trace
     * and every serialised trace already looks at, and `result: false` is exactly what an undecided node
     * used to report. Read [verdict] to tell the two apart.
     */
    val result: Boolean,
    /** What the node answered, including [ConditionVerdict.UNKNOWN] for data the record does not carry. */
    val verdict: ConditionVerdict = ConditionVerdict.of(value = result),
    /**
     * The block a rule's verdict selected, on a [NodeType.RULE] node only.
     *
     * Null everywhere else. This is what records that a rule was answered by its `not_exists` block
     * rather than by `then` or `else`.
     */
    val branch: RuleBranch? = null,
    val evaluationTimeMs: Long? = null,
    val ruleId: String? = null,
    val children: List<DecisionNode> = emptyList()
)
