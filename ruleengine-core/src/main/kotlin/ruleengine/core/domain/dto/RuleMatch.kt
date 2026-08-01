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
    val assignments: Map<String, Any?> = emptyMap()
)
