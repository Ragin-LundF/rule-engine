package ui.builder

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.OperatorNames
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
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
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.ValueExpressionRenderer

/**
 * Maps a parsed [RuleAst] to a [BuilderRule] suitable for the visual Builder editor.
 *
 * Supported constructs:
 * - [ConditionAst] — plain `field operator literal` rows, including `between`, lists and `ignoreCase`
 * - [ComparisonExpressionAst] — symbolic comparisons whose sides may be aggregates, arithmetic,
 *   field paths (including filtered ones, at any depth) or literals
 * - [NotAst] — rendered as a negation flag on the node it wraps
 * - Flat or nested combinations of [AndAst] and [OrAst]
 * - [ActionAst] with [StringLiteral] or [NumberLiteral] arguments
 *
 * Tree structure is preserved via [BuilderConditionNode.Group] nodes so that parenthesized grouping
 * is maintained through the round-trip.
 *
 * Only `then`-block extractions and shapes the engine itself rejects produce
 * [BuilderRule.Unsupported]; the reason names the construct that caused it.
 */
object RuleAstToBuilderMapper {

    fun map(rule: RuleAst): BuilderRule {
        val extraction = rule.actions.firstOrNull { it.extraction != null }
        if (extraction != null) {
            return BuilderRule.Unsupported(
                id = rule.id,
                reason = "Rule uses a regex extraction in its 'then' block, which the Builder cannot edit yet.",
            )
        }

        val conditionNodes = collectNodes(expr = rule.condition, joinToPrevious = "")
            ?: return BuilderRule.Unsupported(
                id = rule.id,
                reason = unsupportedReason(expr = rule.condition),
            )

        return BuilderRule.Supported(
            id = rule.id,
            description = rule.description.orEmpty(),
            conditionNodes = conditionNodes,
            actions = rule.actions.map { mapAction(action = it) },
        )
    }

    // ── recursive node collection ─────────────────────────────────────────────

    /**
     * Recursively collects [BuilderConditionNode] entries, preserving group structure.
     * Returns null for unsupported expressions.
     */
    private fun collectNodes(
        expr: ExpressionAst,
        joinToPrevious: String,
    ): List<BuilderConditionNode>? = when (expr) {
        is ConditionAst -> mapConditionAst(condition = expr, joinToPrevious = joinToPrevious)?.let { listOf(it) }

        is ComparisonExpressionAst -> mapComparisonAst(
            comparison = expr,
            joinToPrevious = joinToPrevious,
        )?.let { listOf(it) }

        is NotAst -> mapNot(expr = expr, joinToPrevious = joinToPrevious)

        is AndAst -> collectGroupedChildren(
            children = expr.children,
            groupJoin = "and",
            parentJoin = joinToPrevious,
        )

        is OrAst -> collectGroupedChildren(
            children = expr.children,
            groupJoin = "or",
            parentJoin = joinToPrevious,
        )
    }

    /**
     * Maps `not <expr>`: a single-node child carries the negation directly, while a multi-node child
     * is wrapped in a negated group so the parentheses survive the round-trip.
     */
    private fun mapNot(
        expr: NotAst,
        joinToPrevious: String,
    ): List<BuilderConditionNode>? {
        val childNodes = collectNodes(expr = expr.child, joinToPrevious = joinToPrevious) ?: return null
        val single = childNodes.singleOrNull()
        if (single != null) {
            return listOf(negate(node = single))
        }
        return listOf(
            BuilderConditionNode.Group(
                nodeId = nextId(prefix = "grp"),
                nodes = childNodes.mapIndexed { index, node ->
                    if (index == 0) withJoin(node = node, join = "") else node
                },
                negated = true,
                joinToPrevious = joinToPrevious,
            )
        )
    }

    private fun negate(node: BuilderConditionNode): BuilderConditionNode = when (node) {
        is BuilderConditionNode.Condition -> node.copy(negated = !node.negated)
        is BuilderConditionNode.Comparison -> node.copy(negated = !node.negated)
        is BuilderConditionNode.Group -> node.copy(negated = !node.negated)
    }

    private fun withJoin(node: BuilderConditionNode, join: String): BuilderConditionNode = when (node) {
        is BuilderConditionNode.Condition -> node.copy(joinToPrevious = join)
        is BuilderConditionNode.Comparison -> node.copy(joinToPrevious = join)
        is BuilderConditionNode.Group -> node.copy(joinToPrevious = join)
    }

    /**
     * Collects children of an And/Or container.
     *
     * Single-condition children are inlined. Multi-node children (from nested
     * containers with a different join type) are wrapped in a [ui.builder.BuilderConditionNode.Group] so that
     * parentheses are preserved in the DSL round-trip.
     */
    private fun collectGroupedChildren(
        children: List<ExpressionAst>,
        groupJoin: String,
        parentJoin: String,
    ): MutableList<BuilderConditionNode>? {
        val result = mutableListOf<BuilderConditionNode>()

        children.forEachIndexed { index, child ->
            val childJoin = if (index == 0) parentJoin else groupJoin
            val nodes = collectNodes(expr = child, joinToPrevious = childJoin) ?: return null

            if (nodes.size == 1) {
                result.add(nodes.single())
            } else {
                // Multiple nodes from a differently-joined sub-expression need a Group
                result.add(
                    BuilderConditionNode.Group(
                        nodeId = "grp-${result.size}",
                        nodes = nodes,
                        joinToPrevious = childJoin,
                    )
                )
            }
        }

        return result
    }

    // ── condition / comparison mapping ────────────────────────────────────────

    private fun mapConditionAst(
        condition: ConditionAst,
        joinToPrevious: String,
    ): BuilderConditionNode.Condition? {
        val literalValue = literalToValue(lit = condition.value) ?: return null
        return BuilderConditionNode.Condition(
            nodeId = nextId(prefix = "nid"),
            field = condition.field,
            operator = normalizeOperator(operator = condition.operator),
            value = literalValue.value,
            valueTo = literalValue.valueTo,
            listItems = literalValue.listItems,
            ignoreCase = condition.ignoreCase,
            joinToPrevious = joinToPrevious,
        )
    }

    private fun mapComparisonAst(
        comparison: ComparisonExpressionAst,
        joinToPrevious: String,
    ): BuilderConditionNode.Comparison? {
        val left = mapValueExpression(expr = comparison.left) ?: return null
        val right = mapValueExpression(expr = comparison.right) ?: return null
        return BuilderConditionNode.Comparison(
            nodeId = nextId(prefix = "cmp"),
            left = left,
            operator = ValueExpressionRenderer.symbol(operator = comparison.operator),
            right = right,
            ignoreCase = comparison.ignoreCase,
            joinToPrevious = joinToPrevious,
        )
    }

    // ── value expressions ─────────────────────────────────────────────────────

    /** Maps one side of a comparison. Returns null for shapes the Builder cannot represent. */
    private fun mapValueExpression(expr: ValueExpressionAst): BuilderOperand? = when (expr) {
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
    private fun mapFieldAccess(expr: FieldAccessAst): BuilderOperand? =
        mapPath(segments = expr.path)?.let { BuilderOperand.FieldRef(path = it) }

    /**
     * Folds a path of any length into [BuilderPathStep]s: every [FieldSegmentAst] opens a step and
     * each following [FilterSegmentAst] attaches to the step it filters.
     */
    private fun mapPath(segments: List<PathSegmentAst>): List<BuilderPathStep>? {
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
    private fun mapFilter(expr: ExpressionAst): BuilderFilter? = when (expr) {
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
                operator = normalizeOperator(operator = expr.operator),
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
    private fun mapArithmetic(expr: ArithmeticValueAst, parenthesized: Boolean): BuilderOperand? {
        val terms = mutableListOf<BuilderTerm>()
        if (!flattenArithmetic(expr = expr, into = terms)) return null
        return BuilderOperand.Calc(terms = terms, parenthesized = parenthesized)
    }

    private fun flattenArithmetic(
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
    private fun mapOperandTerm(expr: ValueExpressionAst): BuilderOperand? =
        if (expr is ArithmeticValueAst) {
            mapArithmetic(expr = expr, parenthesized = true)
        } else {
            mapValueExpression(expr = expr)
        }

    private fun samePrecedence(a: ArithmeticOperatorAst, b: ArithmeticOperatorAst): Boolean =
        precedence(operator = a) == precedence(operator = b)

    private fun precedence(operator: ArithmeticOperatorAst): Int = when (operator) {
        ArithmeticOperatorAst.ADD, ArithmeticOperatorAst.SUBTRACT -> 1
        ArithmeticOperatorAst.MULTIPLY, ArithmeticOperatorAst.DIVIDE -> 2
    }

    // ── actions ───────────────────────────────────────────────────────────────

    private fun mapAction(action: ActionAst): BuilderAction {
        val args = action.arguments.map { literalToValue(lit = it)?.value ?: "?" }
        return BuilderAction(
            id = nextId(prefix = "act"),
            name = action.name,
            arguments = args,
        )
    }

    // ── literals ──────────────────────────────────────────────────────────────

    private fun literalToValue(lit: LiteralAst): LiteralValue? = when (lit) {
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

    private fun literalText(lit: LiteralAst): String? = when (lit) {
        is StringLiteral -> lit.value
        is NumberLiteral -> lit.value
        is BooleanLiteral -> lit.value.toString()
        else -> null
    }

    /**
     * Maps the DSL's word-form operators onto the symbols the Builder dropdowns offer, so a rule
     * written as `amount gt 5` selects the `>` entry instead of showing a value that is not in the list.
     */
    internal fun normalizeOperator(operator: String): String {
        // The engine already knows every spelling of its own operators, so resolve to the canonical
        // name first and only translate what the Builder displays differently.
        val canonical = OperatorUtils.normalizeOperator(op = operator)
        return DISPLAY_SYMBOLS[canonical]
            ?: UNSUPPORTED_SPELLINGS[operator.lowercase()]
            ?: canonical
    }

    /** Ordering comparisons are offered as symbols in the dropdowns, not as their DSL names. */
    private val DISPLAY_SYMBOLS: Map<String, String> = mapOf(
        OperatorNames.GT to OperatorNames.SYMBOL_GT,
        OperatorNames.GTE to OperatorNames.SYMBOL_GTE,
        OperatorNames.LT to OperatorNames.SYMBOL_LT,
        OperatorNames.LTE to OperatorNames.SYMBOL_LTE,
    )

    /**
     * Spellings the engine itself does not accept, mapped so a hand-written rule using one still
     * opens in the Builder rather than locking it. The rule will not compile either way — the
     * Builder just shows what the author meant instead of an operator missing from every dropdown.
     */
    private val UNSUPPORTED_SPELLINGS: Map<String, String> = mapOf(
        "greater_than" to OperatorNames.SYMBOL_GT,
        "greater_or_equal" to OperatorNames.SYMBOL_GTE,
        "less_than" to OperatorNames.SYMBOL_LT,
        "less_or_equal" to OperatorNames.SYMBOL_LTE,
        "ne" to OperatorNames.SYMBOL_NOT_EQUALS,
        "neq" to OperatorNames.SYMBOL_NOT_EQUALS,
        "not_equals" to OperatorNames.SYMBOL_NOT_EQUALS,
    )

    /** Names the construct that prevented mapping, so the lock message is specific. */
    private fun unsupportedReason(expr: ExpressionAst): String {
        val construct = findUnmappable(expr = expr) ?: "an expression"
        return "Rule uses $construct, which the Builder cannot edit yet."
    }

    private fun findUnmappable(expr: ExpressionAst): String? = when (expr) {
        is AndAst -> expr.children.firstNotNullOfOrNull { findUnmappable(expr = it) }
        is OrAst -> expr.children.firstNotNullOfOrNull { findUnmappable(expr = it) }
        is NotAst -> findUnmappable(expr = expr.child)
        is ConditionAst -> if (literalToValue(lit = expr.value) == null) {
            "an unsupported literal value"
        } else {
            null
        }
        is ComparisonExpressionAst -> describeUnmappableOperand(expr = expr.left)
            ?: describeUnmappableOperand(expr = expr.right)
    }

    private fun describeUnmappableOperand(expr: ValueExpressionAst): String? {
        if (mapValueExpression(expr = expr) != null) return null
        return when {
            expr is FunctionCallValueAst -> "a function argument the Builder cannot represent"
            expr is FieldAccessAst -> "a field path the Builder cannot represent"
            else -> "an unsupported value expression"
        }
    }

    // Using a simple counter instead of java.util.UUID so this code can
    // eventually move to commonMain without a JVM dependency.
    private var idCounter = 0

    private fun nextId(prefix: String): String = "$prefix-${idCounter++}"
}
