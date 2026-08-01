package ui.builder

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.OperatorNames
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ui.builder.model.BuilderAction
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderRule

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
     * containers with a different join type) are wrapped in a [ui.builder.model.BuilderConditionNode.Group] so that
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

    private fun mapAction(action: ActionAst): BuilderAction {
        val args = action.arguments.map { literalToValue(lit = it)?.value ?: "?" }
        return BuilderAction(
            id = nextId(prefix = "act"),
            name = action.name,
            arguments = args,
        )
    }

    // ── literals ──────────────────────────────────────────────────────────────

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
