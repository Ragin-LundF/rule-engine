package ui.tester

/**
 * What a single rule decided in a run.
 *
 * Every evaluated rule gets one of these, matched or not. With a rule set built from mutually
 * exclusive pairs — `account-type-checking` / `account-type-not-checking` — "did not match" is the
 * expected answer for roughly half the rules, so it is a result to report, not an absence to hide.
 *
 * @param ruleId     Id of the rule as declared in the DSL.
 * @param matched    Whether the rule's `when` clause held.
 * @param actions    Formatted action strings the rule emitted; empty unless [matched].
 * @param traceRows  Condition rows belonging to this rule only, derived from [traceTree].
 * @param traceTree  The rule's recorded decision tree, kept whole for the trace diagram. Null when
 *   the run produced no readable trace.
 */
data class RuleResult(
    val ruleId: String,
    val matched: Boolean,
    val actions: List<String>,
    val traceRows: List<TraceRow>,
    val traceTree: TraceNode? = null,
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
            matched -> RuleMatchStatus.MATCHED
            traceRows.any { row -> row.result } -> RuleMatchStatus.PARTIAL
            else -> RuleMatchStatus.NO_MATCH
        }
}
