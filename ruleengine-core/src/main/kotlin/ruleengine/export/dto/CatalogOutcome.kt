package ruleengine.export.dto

/**
 * One action a rule produces when it matches, as it appears in the exported overview.
 *
 * [argument] is the action's first argument rendered as text — the engine allows several, but the
 * first is the one that names the outcome (`assessment "service:premium"`), and it is what
 * [ruleengine.evaluator.RuleEngine] itself groups by. [arguments] keeps the full list for the rare
 * action that takes more.
 */
data class CatalogOutcome(
    val action: String,
    val argument: String?,
    val arguments: List<String> = listOfNotNull(argument),
)
