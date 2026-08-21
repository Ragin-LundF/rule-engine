package ruleengine.compiler

import ruleengine.dsl.ast.RuleAst

/**
 * One rule file of a manifest entry: where it came from, and the rules it declares.
 *
 * The engine itself drops this provenance — `RuleEngineBuilder` flattens an entry's files into a single
 * list, because execution does not care which file a rule was written in. Validation does care, for one
 * reason: a diagnostic's line and column are relative to the file that produced it, so reporting on a
 * flattened list can name a line but not the file it is a line of.
 *
 * @param path How the file should be named in a diagnostic — a manifest-relative path, or whatever the
 *   caller resolved it to. It is used as a label, never resolved or read.
 */
data class RuleFileAsts(
    val path: String,
    val asts: List<RuleAst>,
)
