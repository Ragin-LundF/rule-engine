package ui.diagrams

import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.StringLiteral

/** Formats a [LiteralAst] for display inside a condition row. */
internal fun formatLiteral(lit: LiteralAst): String {
    return when (lit) {
        is StringLiteral  -> "\"${lit.value}\""
        is NumberLiteral  -> lit.value
        is ListLiteral    -> "[${lit.items.joinToString(", ") { formatLiteral(it) }}]"
        is BetweenLiteral -> "${lit.low}..${lit.high}"
    }
}


