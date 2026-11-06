package ui.builder

import ruleengine.dsl.ast.ValueExpressionRenderer
import ruleengine.dsl.ast.path.FieldSegmentAst
import ruleengine.dsl.ast.path.FilterSegmentAst
import ruleengine.dsl.ast.path.JoinSegmentAst
import ruleengine.dsl.ast.path.OrEmptySegmentAst
import ruleengine.dsl.ast.path.PathSegmentAst
import ruleengine.dsl.ast.path.SliceSegmentAst
import ruleengine.dsl.ast.path.SortSegmentAst
import ruleengine.dsl.ast.value.ArithmeticOperatorAst
import ruleengine.dsl.ast.value.ArithmeticValueAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.FunctionCallValueAst
import ruleengine.dsl.ast.value.LiteralValueAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.dsl.ast.value.VariableRefAst
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathDecoration
import ui.builder.model.BuilderPathStep
import ui.builder.model.BuilderTerm

// Value expressions — field paths, filters, arithmetic and literals — mapped to Builder operands.
// Split out of RuleAstToBuilderMapper, which owns the condition *tree*. These are pure: unlike the
// tree mapping they generate no ids, which is what makes them safe as free functions.
// Top-level in the same package, so every call site inside the mapper resolves unchanged.

/** Maps one side of a comparison. Returns null for shapes the Builder cannot represent. */
internal fun mapValueExpression(expr: ValueExpressionAst): BuilderOperand? {
    return when (expr) {
        is LiteralValueAst -> literalToOperand(lit = expr.literal)

        is FieldAccessAst -> mapFieldAccess(expr = expr)

        is FunctionCallValueAst -> mapFunctionCall(expr = expr)

        is ArithmeticValueAst -> mapArithmetic(expr = expr, parenthesized = false)

        // A variable read rides on the existing path operand: `OperandText.pathToDsl` writes a
        // single unfiltered segment out verbatim, so `$total` survives the round-trip as itself.
        is VariableRefAst -> BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = "\$${expr.name}")))
    }
}

/**
 * A call becomes an [BuilderOperand.Aggregate] when it is one of the reductions over a single path,
 * and a general [BuilderOperand.Call] otherwise.
 *
 * The split is what keeps every rule written before the wider call forms rendering byte-identically:
 * the aggregate panel with its path breadcrumb is the right editor for `sum(orders.total)`, and the
 * wrong one for `daysBetween(a, b)`, which has no single collection to walk.
 */
internal fun mapFunctionCall(expr: FunctionCallValueAst): BuilderOperand? {
    val function = expr.name.lowercase()
    if (function in OperatorOptions.AGGREGATE_FUNCTIONS) {
        val argument = expr.arguments.singleOrNull() as? FieldAccessAst
        val path = argument?.let { mapPath(segments = it.path) }
        if (path != null) {
            return BuilderOperand.Aggregate(function = function, path = path)
        }
    }
    val args = expr.arguments.map { argument -> mapValueExpression(expr = argument) ?: return null }
    return BuilderOperand.Call(function = expr.name, args = args)
}

/** Any path — plain, dotted, or filtered — becomes a [BuilderOperand.FieldRef] over path steps. */
internal fun mapFieldAccess(expr: FieldAccessAst): BuilderOperand? {
    return mapPath(segments = expr.path)?.let { BuilderOperand.FieldRef(path = it, optional = expr.optional) }
}

/**
 * Folds a path of any length into [BuilderPathStep]s: every [FieldSegmentAst] opens a step, and each
 * filter, slice or sort after it is appended to that step's decorations in the order it was written.
 *
 * The order is kept rather than normalised because it is the meaning:
 * `take(orders, 3)[paid == true]` selects paid orders among the first three, while
 * `take(orders[paid == true], 3)` selects the first three paid orders.
 */
internal fun mapPath(segments: List<PathSegmentAst>): List<BuilderPathStep>? {
    val steps = mutableListOf<BuilderPathStep>()
    for (segment in segments) {
        when (segment) {
            is FieldSegmentAst -> steps.add(BuilderPathStep(name = segment.name))
            is FilterSegmentAst -> {
                val target = steps.lastOrNull() ?: return null
                val filter = mapFilter(expr = segment.expression) ?: return null
                steps[steps.lastIndex] = target.copy(
                    decorations = target.decorations + BuilderPathDecoration.Filter(filter = filter)
                )
            }

            is SliceSegmentAst -> {
                val target = steps.lastOrNull() ?: return null
                steps[steps.lastIndex] = target.copy(
                    decorations = target.decorations + BuilderPathDecoration.Slice(
                        fromEnd = segment.fromEnd,
                        count = segment.count,
                    )
                )
            }

            is SortSegmentAst -> {
                val target = steps.lastOrNull() ?: return null
                steps[steps.lastIndex] = target.copy(
                    decorations = target.decorations + BuilderPathDecoration.Sort(
                        member = segment.member,
                        descending = segment.descending,
                    )
                )
            }
            is OrEmptySegmentAst -> {
                val target = steps.lastOrNull() ?: return null
                steps[steps.lastIndex] = target.copy(decorations = target.decorations + BuilderPathDecoration.OrEmpty)
            }
            // The Builder has no step for joined rows. Mapping nothing locks the rule as Unsupported,
            // which leaves its text untouched instead of rewriting it without the join.
            is JoinSegmentAst -> return null
        }
    }
    return steps.ifEmpty { null }
}


/**
 * Flattens an arithmetic tree into a term list. A sub-expression that binds differently from its
 * parent becomes a nested parenthesized [BuilderOperand.Calc] term, which is what preserves
 * `(a + b) * c` through the round-trip.
 */
internal fun mapArithmetic(expr: ArithmeticValueAst, parenthesized: Boolean): BuilderOperand? {
    val terms = mutableListOf<BuilderTerm>()
    if (!flattenArithmetic(expr = expr, into = terms)) return null
    return BuilderOperand.Calc(terms = terms, parenthesized = parenthesized)
}

internal fun flattenArithmetic(
    expr: ArithmeticValueAst,
    into: MutableList<BuilderTerm>,
): Boolean {
    val symbol = ValueExpressionRenderer.symbol(operator = expr.operator)

    // The parser is left-associative, so the left spine continues the same chain as long as the
    // child binds at the same precedence; otherwise it has to be parenthesized to stay faithful.
    val left = expr.left
    if (left is ArithmeticValueAst && samePrecedence(a = left.operator, b = expr.operator)) {
        if (!flattenArithmetic(expr = left, into = into)) return false
    } else {
        val operand = mapOperandTerm(expr = left) ?: return false
        into.add(BuilderTerm(operator = "", operand = operand))
    }

    val right = mapOperandTerm(expr = expr.right) ?: return false
    into.add(BuilderTerm(operator = symbol, operand = right))
    return true
}

/** Maps a term of an arithmetic chain, parenthesizing a nested chain of different precedence. */
internal fun mapOperandTerm(expr: ValueExpressionAst): BuilderOperand? {
    return if (expr is ArithmeticValueAst) {
        mapArithmetic(expr = expr, parenthesized = true)
    } else {
        mapValueExpression(expr = expr)
    }
}

internal fun samePrecedence(a: ArithmeticOperatorAst, b: ArithmeticOperatorAst): Boolean {
    return precedence(operator = a) == precedence(operator = b)
}

internal fun precedence(operator: ArithmeticOperatorAst): Int {
    return when (operator) {
        ArithmeticOperatorAst.ADD, ArithmeticOperatorAst.SUBTRACT -> 1
        ArithmeticOperatorAst.MULTIPLY, ArithmeticOperatorAst.DIVIDE -> 2
    }
}
