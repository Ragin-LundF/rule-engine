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
 * Maps a parsed [RuleAst] to a [BuilderRule] suitable for the read-only visual Builder view.
 *
 * Supported constructs:
 * - Single [ConditionAst] (field / operator / literal value)
 * - Flat [AndAst] whose children are all [ConditionAst]
 * - [ActionAst] with [StringLiteral] or [NumberLiteral] arguments
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
            val value = literalToString(expr.value) ?: return null
            listOf(BuilderCondition(field = expr.field, operator = expr.operator, value = value))
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

    private fun literalToString(lit: LiteralAst): String? = when (lit) {
        is StringLiteral -> "\"${lit.value}\""
        is NumberLiteral -> lit.value
        is ListLiteral -> lit.items.joinToString(prefix = "[", postfix = "]") { literalToString(it) ?: "?" }
        is BetweenLiteral -> "${lit.low} .. ${lit.high}"
        else -> null
    }

    private fun mapAction(action: ActionAst): BuilderAction {
        val args = action.arguments.map { literalToString(it) ?: "?" }
        return BuilderAction(name = action.name, arguments = args)
    }
}
