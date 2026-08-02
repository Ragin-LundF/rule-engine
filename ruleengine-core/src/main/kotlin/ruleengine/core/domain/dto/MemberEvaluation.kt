package ruleengine.core.domain.dto

/**
 * What one member of a scoped collection produced.
 *
 * Present only when the manifest entry declares a `scope`. [EvaluationResult.matches] still holds
 * every match from every member, in member order, so a consumer that does not care about the split
 * needs no change; [RuleMatch.scopeMember] says which member each of those came from.
 *
 * @property index position of the member in the collection, counting from zero
 * @property key a value identifying the member — its declared `id`, or `<collection>[index]` when it
 *   declares none. The same string appears on every [RuleMatch] the member produced.
 * @property result the member's own outcome, including the variables it published and the rule whose
 *   `stop` ended *its* run. A `stop` halts one member, not the rest of the collection.
 */
data class MemberEvaluation(
    val index: Int,
    val key: String,
    val result: EvaluationResult
)
