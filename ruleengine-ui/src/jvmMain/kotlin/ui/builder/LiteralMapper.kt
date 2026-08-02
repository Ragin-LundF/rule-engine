package ui.builder

import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.VariableRefLiteral
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.LiteralValue

// The literal half of the AST mapping, split from `ValueExpressionMapper` because a literal is read
// three different ways depending on who is asking: as an operand by a comparison or a filter, as a
// decomposed `LiteralValue` by a plain condition row, and as plain text by a list item.

/**
 * Maps a literal to the operand that renders it back.
 *
 * The one place a literal enters the operand model, shared by comparison sides, assignment values and
 * filter predicates — which is what keeps `[status in ["paid", "sent"]]` and
 * `status in ["paid", "sent"]` reading the same list through the same code.
 *
 * [BetweenLiteral] returns null: no operand holds two bounds, and `between` has no comparison form.
 */
internal fun literalToOperand(lit: LiteralAst): BuilderOperand? = when (lit) {
    is StringLiteral -> BuilderOperand.Literal(text = lit.value, numeric = false)
    is NumberLiteral -> BuilderOperand.Literal(text = lit.value, numeric = true)
    // Rendered unquoted by `OperandText.literalToDsl`, so `isActive == true` stays a boolean
    // comparison instead of turning into one against the text "true".
    is BooleanLiteral -> BuilderOperand.Literal(text = lit.value.toString(), numeric = false)
    is ListLiteral -> lit.items
        .map { item -> literalText(lit = item) ?: return null }
        .let { items -> BuilderOperand.ListLiteral(items = items) }
    // A variable read rides on the path operand, the same way `VariableRefAst` does: a single
    // segment named `$total` is written out verbatim and survives the round-trip as itself.
    is VariableRefLiteral -> BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = "\$${lit.name}")))
    else -> null
}

/** A literal decomposed for a plain condition row, which holds `between` bounds and lists apart. */
internal fun literalToValue(lit: LiteralAst): LiteralValue? = when (lit) {
    is StringLiteral -> LiteralValue(value = lit.value)
    is NumberLiteral -> LiteralValue(value = lit.value)
    is BooleanLiteral -> LiteralValue(value = lit.value.toString())
    is ListLiteral -> {
        val items = lit.items.map { item ->
            when (item) {
                is StringLiteral -> item.value
                is NumberLiteral -> item.value
                else -> return null
            }
        }
        LiteralValue(value = "", listItems = items)
    }
    is BetweenLiteral -> LiteralValue(value = lit.low, valueTo = lit.high)
    is VariableRefLiteral -> LiteralValue(value = "\$${lit.name}")
    // `$1` — the capture group of the action's own `extract` clause. Written back out bare by
    // `BuilderToRuleDsl.renderAction`: `OperandText.quoteUnlessNumeric` deliberately keeps an
    // all-digit `$name` quoted, because outside an extraction it is a literal such as a `$100` price.
    is ExtractionRefLiteral -> LiteralValue(value = "\$${lit.groupIndex}")
    else -> null
}

/** A literal as the bare text a list item or a membership source is written with. */
internal fun literalText(lit: LiteralAst): String? = when (lit) {
    is StringLiteral -> lit.value
    is NumberLiteral -> lit.value
    is BooleanLiteral -> lit.value.toString()
    is VariableRefLiteral -> "\$${lit.name}"
    else -> null
}
