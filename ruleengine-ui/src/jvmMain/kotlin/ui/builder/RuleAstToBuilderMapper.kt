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
 * The join word (`and` / `or`) between two consecutive conditions is attached to the
 * second condition so the Builder can edit every link independently.
 * Everything else produces [BuilderRule.Unsupported].
 */
object RuleAstToBuilderMapper {

    fun map(rule: RuleAst): BuilderRule {
        val conditions = mutableListOf<BuilderCondition>()
        val supported = collectConditions(
            expr = rule.condition,
            joinToPrevious = "",
            conditions = conditions,
        )

        if (!supported) {
            return BuilderRule.Unsupported(
                id = rule.id,
                reason = "Rule contains advanced syntax (nested not, extractions, arithmetic). Edit in Code mode.",
            )
        }

        val actions = rule.actions.map { mapAction(it) }

        return BuilderRule.Supported(
            id = rule.id,
            conditions = conditions,
            actions = actions,
        )
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun collectConditions(
        expr: ExpressionAst,
        joinToPrevious: String,
        conditions: MutableList<BuilderCondition>,
    ): Boolean = when (expr) {
        is ConditionAst -> {
            val condition = mapConditionAst(expr) ?: return false
            conditions.add(condition.copy(joinToPrevious = joinToPrevious))
            true
        }
        is AndAst -> {
            expr.children.forEachIndexed { index, child ->
                val childJoin = if (index == 0) joinToPrevious else "and"
                if (!collectConditions(
                        expr = child,
                        joinToPrevious = childJoin,
                        conditions = conditions,
                    )
                ) {
                    return false
                }
            }
            true
        }
        is OrAst -> {
            expr.children.forEachIndexed { index, child ->
                val childJoin = if (index == 0) joinToPrevious else "or"
                if (!collectConditions(
                        expr = child,
                        joinToPrevious = childJoin,
                        conditions = conditions,
                    )
                ) {
                    return false
                }
            }
            true
        }
        else -> false
    }

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

    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}

/** Internal holder for a decomposed literal value. */
private data class LiteralValue(
    val value: String = "",
    val valueTo: String = "",
    val listItems: List<String> = emptyList(),
)
