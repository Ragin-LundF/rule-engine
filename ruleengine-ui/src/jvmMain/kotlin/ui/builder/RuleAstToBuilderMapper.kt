package ui.builder

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral

/**
 * Maps a parsed [RuleAst] to a [BuilderRule] suitable for the visual Builder editor.
 *
 * Supported constructs:
 * - Single [ConditionAst]
 * - Flat or nested combinations of [AndAst] and [OrAst] whose leaves are [ConditionAst]
 * - [ActionAst] with [StringLiteral] or [NumberLiteral] arguments
 * - [BetweenLiteral] values
 * - [ListLiteral] values
 *
 * Tree structure is preserved via [BuilderConditionNode.Group] nodes so that
 * parenthesized grouping is maintained through the round-trip.
 * Everything else produces [BuilderRule.Unsupported].
 */
object RuleAstToBuilderMapper {

    fun map(rule: RuleAst): BuilderRule {
        val conditionNodes = collectNodes(
            expr = rule.condition,
            joinToPrevious = "",
        )

        if (conditionNodes == null) {
            return BuilderRule.Unsupported(
                id = rule.id,
                reason = "Rule contains advanced syntax (nested not, extractions, arithmetic). Edit in Code mode.",
            )
        }

        val actions = rule.actions.map { mapAction(it) }

        return BuilderRule.Supported(
            id = rule.id,
            conditionNodes = conditionNodes,
            actions = actions,
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
        is ConditionAst -> {
            val condition = mapConditionAst(condition = expr) ?: return null
            listOf(
                BuilderConditionNode.Condition(
                    nodeId = condition.id,
                    field = condition.field,
                    operator = condition.operator,
                    value = condition.value,
                    valueTo = condition.valueTo,
                    listItems = condition.listItems,
                    joinToPrevious = joinToPrevious,
                )
            )
        }
        is AndAst -> {
            collectGroupedChildren(
                children = expr.children,
                groupJoin = "and",
                parentJoin = joinToPrevious,
            )
        }
        is OrAst -> {
            collectGroupedChildren(
                children = expr.children,
                groupJoin = "or",
                parentJoin = joinToPrevious,
            )
        }
        else -> null
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

    // ── condition / action mapping ────────────────────────────────────────────

    /**
     * Maps a `ConditionAst` object to a `BuilderCondition` object or returns `null` if the mapping cannot be performed.
     *
     * @param condition The `ConditionAst` object to be transformed.
     *  Contains the field, operator, and value representing a condition in the abstract syntax tree (AST).
     * @return A `BuilderCondition` object if the mapping is successful, otherwise `null`.
     */
    private fun mapConditionAst(condition: ConditionAst): BuilderCondition? {
        val literalValue = literalToValue(condition.value) ?: return null
        return BuilderCondition(
            id = generateId(),
            field = condition.field,
            operator = normalizeOperator(condition.operator),
            value = literalValue.value,
            valueTo = literalValue.valueTo,
            listItems = literalValue.listItems,
            joinToPrevious = "",
        )
    }

    private fun literalToValue(lit: LiteralAst): LiteralValue? = when (lit) {
        is StringLiteral -> LiteralValue(value = lit.value)
        is NumberLiteral -> LiteralValue(value = lit.value)
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

    private fun mapAction(action: ActionAst): BuilderAction {
        val args = action.arguments.map { literalToValue(it)?.value ?: "?" }
        return BuilderAction(
            id = generateId(),
            name = action.name,
            arguments = args,
        )
    }

    private fun normalizeOperator(operator: String): String = operator

    // Using a simple counter instead of java.util.UUID so this code can
    // eventually move to commonMain without a JVM dependency.
    private var idCounter = 0

    private fun generateId(): String = "nid-${idCounter++}"
}

/** Internal holder for a decomposed literal value. */
private data class LiteralValue(
    val value: String = "",
    val valueTo: String = "",
    val listItems: List<String> = emptyList(),
)
