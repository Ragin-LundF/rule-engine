package ui.tester.model

import ruleengine.core.domain.dto.ConditionVerdict

/**
 * A single condition trace row produced by the evaluator.
 *
 * @param label   Human-readable description of the condition (e.g. "purpose contains rent").
 * @param result  Whether the condition evaluated to true.
 * @param verdict What the condition answered. [ConditionVerdict.UNKNOWN] means the record carried no
 *   data to decide it, which a red row would misreport as "the condition did not hold".
 * @param actual  The value actually found, when the evaluator recorded one. Null for the condition
 *   types that do not yet report it, in which case the row renders exactly as it always has.
 */
data class TraceRow(
    val label: String,
    val result: Boolean,
    val verdict: ConditionVerdict = ConditionVerdict.of(value = result),
    val actual: String? = null,
)
