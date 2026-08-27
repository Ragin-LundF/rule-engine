package ruleengine.compiler.operators

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.numeric.IntegerBetweenExpression
import ruleengine.evaluator.compiled.numeric.IntegerComparisonExpression
import ruleengine.evaluator.compiled.numeric.IntegerInExpression
import ruleengine.evaluator.compiled.numeric.IntegerComparisonOperator

object IntegerOperator {
    /** [op] is the canonical operator name; aliases are already resolved by the caller. */
    @Suppress("ThrowsCount")
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId, op: String): CompiledExpression {
        if (op == OperatorNames.BETWEEN) {
            val between = cond.value as? BetweenLiteral ?: throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'between' expects two integer bounds for field '${cond.field}'"
            )
            val low = runCatching { between.low.toLong() }.getOrElse {
                throw CompilationException(
                    ruleId = ruleId,
                    details = "Invalid lower bound: ${between.low}"
                )
            }
            val high = runCatching { between.high.toLong() }.getOrElse {
                throw CompilationException(
                    ruleId = ruleId,
                    details = "Invalid upper bound: ${between.high}"
                )
            }
            return IntegerBetweenExpression(field = fieldId, low = low, high = high)
        }

        if (op == OperatorNames.IN) {
            return IntegerInExpression(field = fieldId, expected = membershipSet(ruleId = ruleId, cond = cond))
        }

        val literal = cond.value as? NumberLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected numeric literal for integer field '${cond.field}'"
        )
        val expected = runCatching { literal.value.toLong() }.getOrElse {
            throw CompilationException(
                ruleId = ruleId,
                details = "Invalid integer literal: ${literal.value}"
            )
        }

        val comparison = COMPARISONS[op] ?: throw CompilationException(
            ruleId = ruleId,
            details = "Unsupported operator '${cond.operator}' for integer field"
        )

        return IntegerComparisonExpression(field = fieldId, expected = expected, op = comparison)
    }

    /**
     * The values an `in` tests against.
     *
     * A bare literal counts as a set of one, matching how `TextInOperator` reads `status in "paid"` —
     * the brackets are what a reader expects, not what the grammar demands.
     */
    private fun membershipSet(ruleId: String?, cond: ConditionAst): Set<Long> {
        val items = when (val literal = cond.value) {
            is ListLiteral -> literal.items
            is NumberLiteral -> listOf(literal)
            else -> emptyList()
        }
        if (items.isEmpty()) {
            throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'in' expects a list of whole numbers for integer field '${cond.field}'"
            )
        }
        return items.mapTo(mutableSetOf()) { item ->
            (item as? NumberLiteral)?.value?.toLongOrNull() ?: throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'in' expects whole numbers for integer field '${cond.field}'"
            )
        }
    }

    /** Keyed by canonical name only — the caller normalises every alias before it gets here. */
    private val COMPARISONS: Map<String, IntegerComparisonOperator> = mapOf(
        OperatorNames.EQUALS to IntegerComparisonOperator.EQ,
        OperatorNames.GT to IntegerComparisonOperator.GT,
        OperatorNames.GTE to IntegerComparisonOperator.GTE,
        OperatorNames.LT to IntegerComparisonOperator.LT,
        OperatorNames.LTE to IntegerComparisonOperator.LTE,
    )
}

