package ruleengine.dsl.ast

import ruleengine.dsl.ast.condition.AndAst
import ruleengine.dsl.ast.condition.ComparisonExpressionAst
import ruleengine.dsl.ast.condition.ComparisonOperatorAst
import ruleengine.dsl.ast.condition.ConditionAst
import ruleengine.dsl.ast.condition.ExpressionAst
import ruleengine.dsl.ast.condition.NotAst
import ruleengine.dsl.ast.condition.OrAst
import ruleengine.dsl.ast.literal.BetweenLiteral
import ruleengine.dsl.ast.literal.BooleanLiteral
import ruleengine.dsl.ast.literal.ExtractionRefLiteral
import ruleengine.dsl.ast.literal.ListLiteral
import ruleengine.dsl.ast.literal.LiteralAst
import ruleengine.dsl.ast.literal.NumberLiteral
import ruleengine.dsl.ast.literal.StringLiteral
import ruleengine.dsl.ast.literal.VariableRefLiteral
import ruleengine.dsl.ast.path.FieldSegmentAst
import ruleengine.dsl.ast.path.FilterSegmentAst
import ruleengine.dsl.ast.path.JoinSegmentAst
import ruleengine.dsl.ast.path.PathSegmentAst
import ruleengine.dsl.ast.path.SliceSegmentAst
import ruleengine.dsl.ast.path.SortSegmentAst
import ruleengine.dsl.ast.rule.AssignmentKindAst
import ruleengine.dsl.ast.rule.VariableAssignmentAst
import ruleengine.dsl.ast.value.ArithmeticOperatorAst
import ruleengine.dsl.ast.value.ArithmeticValueAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.FunctionCallValueAst
import ruleengine.dsl.ast.value.LiteralValueAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.dsl.ast.value.VariableRefAst

/**
 * Renders a parsed expression back to DSL-like text.
 *
 * Needed because the trace has to label a condition with something a rule author recognises, and by
 * the time a rule is compiled the text is gone: the AST carries no source positions (the parser drops
 * [ruleengine.dsl.lexer.Token] line/column when it builds nodes), and the compiled form has already
 * rewritten path roots from alias to canonical id, so neither can reproduce what was written.
 *
 * The output is close to the original source but not guaranteed to be byte-identical to it —
 * whitespace and redundant parentheses are normalised, and operators are rendered in one canonical
 * spelling per operator rather than in whichever of its aliases the author used.
 */
object ValueExpressionRenderer {

    /** Renders an operand of a comparison, e.g. `count(orders[status == "paid"])` or `a.b + 1`. */
    fun render(expr: ValueExpressionAst): String {
        return when (expr) {
            is FieldAccessAst -> renderPath(path = expr.path)
            is LiteralValueAst -> renderLiteral(literal = expr.literal)
            is VariableRefAst -> $$"$$${expr.name}"
            is FunctionCallValueAst -> {
                val args = expr.arguments.joinToString(separator = ", ") { argument -> render(expr = argument) }
                "${expr.name}($args)"
            }

            is ArithmeticValueAst -> {
                // Parenthesised unconditionally: the AST has already resolved precedence, and
                // reconstructing which parentheses were redundant is not worth the ambiguity in a label.
                val left = render(expr = expr.left)
                val right = render(expr = expr.right)
                "($left ${symbol(operator = expr.operator)} $right)"
            }
        }
    }

    /** Renders a whole condition, used for the predicate inside a `[...]` filter segment. */
    fun renderExpression(expr: ExpressionAst): String {
        return when (expr) {
            is ConditionAst -> "${expr.field} ${expr.operator} ${renderLiteral(literal = expr.value)}"
            is ComparisonExpressionAst ->
                "${render(expr = expr.left)} ${symbol(operator = expr.operator)} ${render(expr = expr.right)}"

            is NotAst -> "not ${renderExpression(expr = expr.child)}"
            is AndAst -> expr.children.joinToString(separator = " and ") { child -> renderExpression(expr = child) }
            is OrAst -> expr.children.joinToString(separator = " or ") { child -> renderExpression(expr = child) }
        }
    }

    private fun renderPath(path: List<PathSegmentAst>): String {
        val builder = StringBuilder()
        path.forEach { segment ->
            when (segment) {
                is FieldSegmentAst -> {
                    if (builder.isNotEmpty()) {
                        builder.append('.')
                    }
                    builder.append(segment.name)
                }
                // A filter binds to the segment before it, so it appends without a separating dot.
                is FilterSegmentAst -> {
                    builder.append('[')
                    builder.append(renderExpression(expr = segment.expression))
                    builder.append(']')
                }
                // A slice wraps everything read so far rather than appending to it, which is what
                // the `take(orders, 3)` spelling says and what makes `.total` after it read right.
                is SliceSegmentAst -> {
                    val call = if (segment.fromEnd) "takeLast" else "take"
                    val inner = builder.toString()
                    builder.setLength(0)
                    builder.append(call).append('(').append(inner).append(", ").append(segment.count).append(')')
                }
                // Always the first segment, so it starts the text everything after it continues.
                // The key and the `alias:` spelling are written exactly as `Parser.parseJoinByKey`
                // reads them back.
                is JoinSegmentAst -> builder.append(renderJoin(segment = segment))
                // A sort wraps what came before it for the same reason a slice does. The member
                // is quoted and the direction is not, matching exactly what `Parser.parseSort`
                // reads back — this text is what the Builder round-trips through.
                is SortSegmentAst -> {
                    val inner = builder.toString()
                    val member = segment.member?.let { name -> "\"$name\", " }.orEmpty()
                    val direction = if (segment.descending) "desc" else "asc"
                    builder.setLength(0)
                    builder.append("sortBy(").append(inner).append(", ").append(member)
                        .append(direction).append(')')
                }
            }
        }
        return builder.toString()
    }

    private fun renderJoin(segment: JoinSegmentAst): String {
        val key = listOfNotNull(segment.key?.let { name -> "\"$name\"" })
        val sources = segment.sources.map { source -> "${source.alias}: ${render(expr = source.path)}" }
        return (key + sources).joinToString(separator = ", ", prefix = "joinByKey(", postfix = ")")
    }

    /**
     * [value] as a DSL string literal that the lexer reads back to exactly [value].
     *
     * Only `\\` and `\"` are escapes, so a backslash is escaped only where it would otherwise be read
     * as one — before `\`, before `"` or at the end. A regex's `\d` is rendered as written.
     */
    private fun quote(value: String): String {
        val out = StringBuilder(value.length + 2).append('"')
        value.forEachIndexed { index, char ->
            val next = value.getOrNull(index = index + 1)
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' && (next == null || next == '\\' || next == '"') -> out.append("\\\\")
                else -> out.append(char)
            }
        }
        return out.append('"').toString()
    }

    private fun renderLiteral(literal: LiteralAst): String {
        return when (literal) {
            is StringLiteral -> quote(value = literal.value)
            is NumberLiteral -> literal.value
            is BooleanLiteral -> literal.value.toString()
            is ListLiteral -> {
                val items = literal.items.joinToString(separator = ", ") { item -> renderLiteral(literal = item) }
                "[$items]"
            }
            // Space-separated, not `low..high`: `between` takes two literals in the DSL, and this text
            // is read as the condition the author wrote.
            is BetweenLiteral -> "${literal.low} ${literal.high}"
            is ExtractionRefLiteral -> $$"$$${literal.groupIndex}"
            is VariableRefLiteral -> $$"$$${literal.name}"
        }
    }

    /**
     * Renders an assignment clause, e.g. `set orderTotal = sum(orders[*].amount)` or
     * `add "billing" to topics`.
     */
    fun renderAssignment(assignment: VariableAssignmentAst): String {
        val value = render(expr = assignment.expression)
        return when (assignment.kind) {
            AssignmentKindAst.SET -> "set ${assignment.name} = $value"
            AssignmentKindAst.ADD -> "add $value to ${assignment.name}"
        }
    }

    /** The DSL spelling of a comparison operator, e.g. [ComparisonOperatorAst.GTE] -> `>=`. */
    fun symbol(operator: ComparisonOperatorAst): String {
        return when (operator) {
            ComparisonOperatorAst.EQ -> "=="
            ComparisonOperatorAst.NEQ -> "!="
            ComparisonOperatorAst.GT -> ">"
            ComparisonOperatorAst.GTE -> ">="
            ComparisonOperatorAst.LT -> "<"
            ComparisonOperatorAst.LTE -> "<="
            ComparisonOperatorAst.CONTAINS -> "contains"
            ComparisonOperatorAst.IN -> "in"
        }
    }

    /** The DSL spelling of an arithmetic operator, e.g. [ArithmeticOperatorAst.MULTIPLY] -> `*`. */
    fun symbol(operator: ArithmeticOperatorAst): String {
        return when (operator) {
            ArithmeticOperatorAst.ADD -> "+"
            ArithmeticOperatorAst.SUBTRACT -> "-"
            ArithmeticOperatorAst.MULTIPLY -> "*"
            ArithmeticOperatorAst.DIVIDE -> "/"
        }
    }
}
