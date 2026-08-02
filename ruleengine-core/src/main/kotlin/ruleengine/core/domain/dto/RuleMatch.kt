package ruleengine.core.domain.dto

data class RuleMatch(
    val ruleId: String,
    val actions: List<RuleAction>,
    /**
     * Variables this rule published through its `set` clauses, in assignment order.
     *
     * Empty for a rule that assigns none. Attribution lives here rather than in
     * [EvaluationResult.variables], which only carries the final value of each variable and so
     * cannot say which rule wrote it when several assign the same name.
     */
    val assignments: Map<String, Any?> = emptyMap(),
    /**
     * Which half of the rule produced [actions] and [assignments].
     *
     * [RuleBranch.THEN] means the rule's condition held. [RuleBranch.ELSE] means it did not and the
     * rule declared an `else` block — so a match is not by itself proof that the condition was true.
     * A rule without an `else` block can only ever report [RuleBranch.THEN], which is why that is the
     * default.
     */
    val branch: RuleBranch = RuleBranch.THEN
)
