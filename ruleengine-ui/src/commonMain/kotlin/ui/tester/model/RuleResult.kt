package ui.tester.model

import ruleengine.core.domain.dto.RuleBranch

/**
 * What a single rule decided in a run.
 *
 * Every evaluated rule gets one of these, matched or not. With a rule set built from mutually
 * exclusive pairs — `account-type-checking` / `account-type-not-checking` — "did not match" is the
 * expected answer for roughly half the rules, so it is a result to report, not an absence to hide.
 *
 * @param ruleId     Id of the rule as declared in the DSL.
 * @param matched    Whether the rule's `when` clause held. False for a rule whose `else` branch fired:
 *   producing output is not the same as the condition being true.
 * @param branch     Which branch produced [actions] and [assignments], or null when the rule produced
 *   nothing at all.
 * @param actions    Formatted action strings the rule emitted, from whichever branch fired; empty when
 *   [branch] is null.
 * @param assignments Formatted `set` results the rule published, e.g. `orderTotal = 300`; empty when
 *   [branch] is null. Shown per rule rather than as one final map, because that is what says which
 *   rule a value came from when several assign the same name.
 * @param traceRows  Condition rows belonging to this rule only, derived from [traceTree].
 * @param traceTree  The rule's recorded decision tree, kept whole for the trace diagram. Null when
 *   the run produced no readable trace.
 */
data class RuleResult(
    val ruleId: String,
    val matched: Boolean,
    val actions: List<String>,
    val assignments: List<String> = emptyList(),
    val traceRows: List<TraceRow>,
    val traceTree: TraceNode? = null,
    val branch: RuleBranch? = null,
    /** True when an earlier rule's `stop` ended the run before this rule was reached. */
    val notEvaluated: Boolean = false,
) {
    /**
     * Derived here rather than in the view so the dot, the badge colour, the badge label and the
     * roster filter all read one definition instead of repeating the same three-way branch.
     *
     * A match wins outright even with false conditions in the trace — an `or` rule fires with
     * branches that did not hold, and a rule that fired is a match however it got there.
     *
     * [PARTIAL] deliberately means "the trace has something green in it", not "part of this rule is
     * satisfiable". `AndExpression` sorts children by cost and stops at the first failure, so a
     * condition that would have held is never evaluated and never recorded when a cheaper one fails
     * first — which makes [PARTIAL] rarer than the rule text suggests.
     *
     * Tying the status to the recorded trace rather than to the rule text is the safer of the two:
     * the badge can never claim more than the trace the user can expand and read underneath it.
     */
    val status: RuleMatchStatus
        get() = when {
            // First: a rule that never ran has no verdict to report, and every branch below would be
            // reading a trace it does not have.
            notEvaluated -> RuleMatchStatus.NOT_EVALUATED
            matched -> RuleMatchStatus.MATCHED
            // Checked before PARTIAL: a rule whose else branch fired has a definite answer, and
            // "some conditions held" would report the near miss instead of the output it produced.
            branch == RuleBranch.ELSE -> RuleMatchStatus.ELSE_MATCHED
            traceRows.any { row -> row.result } -> RuleMatchStatus.PARTIAL
            else -> RuleMatchStatus.NO_MATCH
        }
}
