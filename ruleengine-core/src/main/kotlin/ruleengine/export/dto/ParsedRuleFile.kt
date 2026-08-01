package ruleengine.export.dto

import ruleengine.dsl.ast.RuleAst

/**
 * One `.rule` file's parsed rules, paired with the path the manifest lists it under.
 *
 * The input to [ruleengine.export.RuleCatalogBuilder] when the caller has already parsed the rules —
 * the workbench holds them for the open entry, and re-reading from disk would both duplicate the
 * work and export the saved file rather than what the author is looking at.
 */
data class ParsedRuleFile(
    val relativePath: String,
    val rules: List<RuleAst>,
)
