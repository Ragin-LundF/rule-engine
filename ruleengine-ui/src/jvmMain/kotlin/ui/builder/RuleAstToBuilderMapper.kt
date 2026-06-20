package ui.builder

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral

/**
 * Maps a parsed [RuleAst] to a [BuilderRule] suitable for the visual Builder editor.
 *
 * Supported constructs:
 * - Single [ConditionAst] (field / operator / literal value)
 * - Flat [AndAst] whose children are all [ConditionAst]
 * - [ActionAst] with [StringLiteral] or [NumberLiteral] arguments
 * - [BetweenLiteral] values
 * - [ListLiteral] values
 *
 * Everything else produces [BuilderRule.Unsupported].
 */
object RuleAstToBuilderMapper {

    fun map(rule: RuleAst): BuilderRule {
        val conditions = extractConditions(rule.condition)
            ?: return BuilderRule.Unsupported(
                id = rule.id,
                reason = "Rule contains advanced syntax (nested or/not, extractions, arithmetic). Edit in Code mode.",
            )

        val join = when {
            conditions.size <= 1 -> ConditionJoin.SINGLE
            else -> ConditionJoin.AND
        }

        val actions = rule.actions.map { mapAction(it) }

        return BuilderRule.Supported(
            id = rule.id,
            conditions = conditions,
            conditionJoin = join,
            actions = actions,
        )
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun extractConditions(expr: ExpressionAst): List<BuilderCondition>? = when (expr) {
        is ConditionAst -> {
            mapConditionAst(expr)?.let { listOf(it) }
        }
        is AndAst -> {
            val rows = mutableListOf<BuilderCondition>()
            for (child in expr.children) {
                val childConditions = extractConditions(child) ?: return null
                rows += childConditions
            }
            rows
        }
        else -> null // OrAst, NotAst, ComparisonExpressionAst, etc. → unsupported
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
        )
    }

    private fun literalToValue(lit: LiteralAst): LiteralValue? = when (lit) {
        is StringLiteral -> LiteralValue(value = "\"${lit.value}\"")
        is NumberLiteral -> LiteralValue(value = lit.value)
        is ListLiteral -> {
            val items = lit.items.map { item ->
                when (item) {
                    is StringLiteral -> "\"${item.value}\""
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
