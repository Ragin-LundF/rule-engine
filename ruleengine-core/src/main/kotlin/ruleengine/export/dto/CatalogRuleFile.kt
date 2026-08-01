package ruleengine.export.dto

/**
 * The rules of one `.rule` file, keeping the file they were written in.
 *
 * The engine drops this grouping — [ruleengine.builder.RuleEngineBuilder] flattens an entry's files
 * into one list because execution does not care where a rule was written. An export does care: the
 * files are how the author organised the rule set, and that organisation is what makes a long
 * document navigable.
 */
data class CatalogRuleFile(
    val relativePath: String,
    val rules: List<CatalogRule>,
)
