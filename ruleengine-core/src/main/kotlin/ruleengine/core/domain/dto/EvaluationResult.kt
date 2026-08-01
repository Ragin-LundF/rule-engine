package ruleengine.core.domain.dto

data class EvaluationResult(
    val matches: List<RuleMatch>,
    val trace: Any? = null,
    /**
     * Final value of every variable a matching rule published through a `set` clause, keyed by name
     * without the `$` prefix.
     *
     * Reflects the state after the last rule ran, so a variable assigned by several matching rules
     * carries the last write. Use [RuleMatch.assignments] to see which rule wrote what.
     */
    val variables: Map<String, Any?> = emptyMap()
)
