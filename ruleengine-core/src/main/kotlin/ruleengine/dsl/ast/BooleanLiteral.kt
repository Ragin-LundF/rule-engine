package ruleengine.dsl.ast

/**
 * A `true` or `false` literal.
 *
 * The lexer has no dedicated boolean token — `true` and `false` arrive as identifiers and are
 * recognised in value position by the parser, so an ordinary field may still be named `true`.
 */
data class BooleanLiteral(
    val value: Boolean
) : LiteralAst
