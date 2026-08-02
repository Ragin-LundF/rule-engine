package ruleengine.core.domain.dto

data class EvaluationResult(
    /**
     * Every rule that produced output, in evaluation order.
     *
     * A rule appears here when its condition held, or when the condition was false and the rule
     * declared an `else` block — [RuleMatch.branch] says which. Filter on
     * [RuleBranch.THEN] for the rules whose condition actually held.
     */
    val matches: List<RuleMatch>,
    val trace: Any? = null,
    /**
     * Final value of every variable a matching rule published through a `set` clause, keyed by name
     * without the `$` prefix.
     *
     * Reflects the state after the last rule ran, so a variable assigned by several matching rules
     * carries the last write. Use [RuleMatch.assignments] to see which rule wrote what.
     */
    val variables: Map<String, Any?> = emptyMap(),
    /**
     * Id of the rule whose `stop` ended the run, or null when every rule was evaluated.
     *
     * Without it a consumer cannot tell *"no further rule matched"* from *"no further rule ran"* — the
     * rules after this one produced nothing because they were never reached.
     */
    val stoppedBy: String? = null,
    /**
     * One entry per member of the scoped collection, or empty for a whole-document evaluation.
     *
     * `variables` and `stoppedBy` are per member and live inside each entry: a `stop` ends one
     * member's run and says nothing about the next. At this level they are empty and null for a
     * scoped result, while [matches] is the concatenation across members so that a consumer written
     * before scoping still sees everything that fired.
     */
    val members: List<MemberEvaluation> = emptyList()
)
