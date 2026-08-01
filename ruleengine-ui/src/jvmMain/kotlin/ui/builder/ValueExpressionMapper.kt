package ui.builder

import ruleengine.dsl.ast.ArithmeticOperatorAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ui.builder.model.BuilderFilter
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.BuilderTerm
import ui.builder.model.LiteralValue

// Value expressions — field paths, filters, arithmetic and literals — mapped to Builder operands.
// Split out of RuleAstToBuilderMapper, which owns the condition *tree*. These are pure: unlike the
// tree mapping they generate no ids, which is what makes them safe as free functions.
// Top-level in the same package, so every call site inside the mapper resolves unchanged.

/** Maps one side of a comparison. Returns null for shapes the Builder cannot represent. */
internal fun mapValueExpression(expr: ValueExpressionAst): BuilderOperand? = when (expr) {
    is LiteralValueAst -> when (val literal = expr.literal) {
        is StringLiteral -> BuilderOperand.Literal(text = literal.value, numeric = false)
        is NumberLiteral -> BuilderOperand.Literal(text = literal.value, numeric = true)
        else -> null
    }

    is FieldAccessAst -> mapFieldAccess(expr = expr)

    is FunctionCallValueAst -> {
        val argument = expr.arguments.singleOrNull() as? FieldAccessAst
        val path = argument?.let { mapPath(segments = it.path) }
        if (path == null) null else BuilderOperand.Aggregate(function = expr.name.lowercase(), path = path)
    }

    is ArithmeticValueAst -> mapArithmetic(expr = expr, parenthesized = false)
}

/** Any path — plain, dotted, or filtered — becomes a [BuilderOperand.FieldRef] over path steps. */
internal fun mapFieldAccess(expr: FieldAccessAst): BuilderOperand? =
    mapPath(segments = expr.path)?.let { BuilderOperand.FieldRef(path = it) }

/**
 * Folds a path of any length into [BuilderPathStep]s: every [FieldSegmentAst] opens a step and
 * each following [FilterSegmentAst] attaches to the step it filters.
 */
internal fun mapPath(segments: List<PathSegmentAst>): List<BuilderPathStep>? {
    val steps = mutableListOf<BuilderPathStep>()
    for (segment in segments) {
        when (segment) {
            is FieldSegmentAst -> steps.add(BuilderPathStep(name = segment.name))
            is FilterSegmentAst -> {
                val target = steps.lastOrNull() ?: return null
                val filter = mapFilter(expr = segment.expression) ?: return null
                steps[steps.lastIndex] = target.copy(filters = target.filters + filter)
            }
        }
    }
    return steps.ifEmpty { null }
}

/**
 * Maps a filter expression. Only single comparisons against a literal are representable.
 *
 * The compared field may be a dotted path into the element — `parcels[origin.hub == "HAM"]`
 * reads `origin.hub` relative to a parcel, which the engine resolves through the element context.
 * A filter nested inside the filtered path is not representable: [BuilderFilter] is a flat
 * `field op value` row, so `OperandText` would drop the inner brackets.
 */
internal fun mapFilter(expr: ExpressionAst): BuilderFilter? = when (expr) {
    is ComparisonExpressionAst -> {
        val field = (expr.left as? FieldAccessAst)?.path
            ?.takeIf { segments -> segments.all { it is FieldSegmentAst } }
            ?.joinToString(separator = ".") { (it as FieldSegmentAst).name }
        val value = (expr.right as? LiteralValueAst)?.literal?.let { literalText(lit = it) }
        if (field == null || value == null) {
            null
        } else {
            BuilderFilter(
                field = field,
                operator = ValueExpressionRenderer.symbol(operator = expr.operator),
                value = value,
            )
        }
    }

    is ConditionAst -> literalText(lit = expr.value)?.let { value ->
        BuilderFilter(
            field = expr.field,
            operator = RuleAstToBuilderMapper.normalizeOperator(operator = expr.operator),
            value = value,
        )
    }

    else -> null
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
internal fun mapOperandTerm(expr: ValueExpressionAst): BuilderOperand? =
    if (expr is ArithmeticValueAst) {
        mapArithmetic(expr = expr, parenthesized = true)
    } else {
        mapValueExpression(expr = expr)
    }

internal fun samePrecedence(a: ArithmeticOperatorAst, b: ArithmeticOperatorAst): Boolean =
    precedence(operator = a) == precedence(operator = b)

internal fun precedence(operator: ArithmeticOperatorAst): Int = when (operator) {
    ArithmeticOperatorAst.ADD, ArithmeticOperatorAst.SUBTRACT -> 1
    ArithmeticOperatorAst.MULTIPLY, ArithmeticOperatorAst.DIVIDE -> 2
}

// ── actions ───────────────────────────────────────────────────────────────

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
    else -> null
}

internal fun literalText(lit: LiteralAst): String? = when (lit) {
    is StringLiteral -> lit.value
    is NumberLiteral -> lit.value
    is BooleanLiteral -> lit.value.toString()
    else -> null
}
