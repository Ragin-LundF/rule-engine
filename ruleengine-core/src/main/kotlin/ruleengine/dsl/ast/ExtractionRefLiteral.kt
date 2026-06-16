package ruleengine.dsl.ast

/**
 * A literal AST node that represents a reference to an extracted value produced
 * by an [ExtractionAst] in the same action (written as `$1` in the rule DSL).
 *
 * The [groupIndex] is the 1-based capture group referred to. `0` refers to
 * the whole match.
 */
data class ExtractionRefLiteral(val groupIndex: Int) : LiteralAst

