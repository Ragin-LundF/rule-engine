package ui.diagrams.model

import ruleengine.dsl.ast.RuleAst

/**
 * The rules parsed from one `.rule` file of a manifest entry, together with the file they came from.
 *
 * The core deliberately drops this provenance: `RuleEngineBuilder.loadRuleAsts` flattens an entry's
 * rule files into a single `List<RuleAst>` because execution does not care which file a rule was
 * written in. The diagram does care — it labels the file bands that show grouping into files is an
 * organisation choice and not a runtime boundary — so the UI parses per file instead of parsing the
 * concatenated text.
 */
data class RuleSource(
    val relativePath: String,
    val rules: List<RuleAst>,
)
