package ui.diagrams

import ruleengine.dsl.ast.literal.BetweenLiteral
import ruleengine.dsl.ast.literal.BooleanLiteral
import ruleengine.dsl.ast.literal.ExtractionRefLiteral
import ruleengine.dsl.ast.literal.ListLiteral
import ruleengine.dsl.ast.literal.LiteralAst
import ruleengine.dsl.ast.literal.NumberLiteral
import ruleengine.dsl.ast.literal.StringLiteral
import ruleengine.dsl.ast.literal.VariableRefLiteral

/** Formats a [LiteralAst] for display inside a condition row. */
internal fun formatLiteral(lit: LiteralAst): String {
    return when (lit) {
        is StringLiteral  -> "\"${lit.value}\""
        is NumberLiteral  -> lit.value
        is BooleanLiteral -> lit.value.toString()
        is ListLiteral    -> "[${lit.items.joinToString(", ") { formatLiteral(it) }}]"
        is BetweenLiteral -> "${lit.low}..${lit.high}"
        is ExtractionRefLiteral -> $$"$$${lit.groupIndex}"
        is VariableRefLiteral -> $$"$$${lit.name}"
    }
}

