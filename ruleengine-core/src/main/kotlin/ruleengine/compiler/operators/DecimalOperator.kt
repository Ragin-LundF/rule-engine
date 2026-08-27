package ruleengine.compiler.operators

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.numeric.ComparisonOperator
import ruleengine.evaluator.compiled.numeric.DecimalBetweenExpression
import ruleengine.evaluator.compiled.numeric.DecimalComparisonExpression
import ruleengine.evaluator.compiled.numeric.DecimalInExpression
import java.math.BigDecimal

object DecimalOperator {
    /** [op] is the canonical operator name; aliases are already resolved by the caller. */
    @Suppress("ThrowsCount")
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId, op: String): CompiledExpression {
        if (op == OperatorNames.BETWEEN) {
            val between = cond.value as? BetweenLiteral ?: throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'between' expects two numeric bounds for field '${cond.field}'"
            )
            val low = runCatching { BigDecimal(between.low) }.getOrElse {
                throw CompilationException(
                    ruleId = ruleId,
                    details = "Invalid lower bound: ${between.low}"
                )
            }
            val high = runCatching { BigDecimal(between.high) }.getOrElse {
                throw CompilationException(
                    ruleId = ruleId,
                    details = "Invalid upper bound: ${between.high}"
                )
            }
            return DecimalBetweenExpression(field = fieldId, low = low, high = high)
        }

        if (op == OperatorNames.IN) {
            return DecimalInExpression(field = fieldId, expected = membershipSet(ruleId = ruleId, cond = cond))
        }

        val literal = cond.value as? NumberLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected numeric literal for decimal field '${cond.field}'"
        )
        val expected = runCatching { BigDecimal(literal.value) }.getOrElse {
            throw CompilationException(
                ruleId = ruleId,
                details = "Invalid decimal literal: ${literal.value}"
            )
        }

        val comparison = COMPARISONS[op] ?: throw CompilationException(
            ruleId = ruleId,
            details = "Unsupported operator '${cond.operator}' for decimal field"
        )

        return DecimalComparisonExpression(field = fieldId, expected = expected, op = comparison)
    }

    /**
     * The values an `in` tests against.
     *
     * A bare literal counts as a set of one, matching how `TextInOperator` reads `status in "paid"` —
     * the brackets are what a reader expects, not what the grammar demands.
     */
    private fun membershipSet(ruleId: String?, cond: ConditionAst): Set<BigDecimal> {
        val items = when (val literal = cond.value) {
            is ListLiteral -> literal.items
            is NumberLiteral -> listOf(literal)
            else -> emptyList()
        }
        if (items.isEmpty()) {
            throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'in' expects a list of numbers for decimal field '${cond.field}'"
            )
        }
        return items.mapTo(mutableSetOf()) { item ->
            val number = (item as? NumberLiteral)?.value
            runCatching { BigDecimal(number) }.getOrElse {
                throw CompilationException(
                    ruleId = ruleId,
                    details = "Operator 'in' expects numbers for decimal field '${cond.field}'"
                )
            }
        }
    }

    /** Keyed by canonical name only — the caller normalises every alias before it gets here. */
    private val COMPARISONS: Map<String, ComparisonOperator> = mapOf(
        OperatorNames.EQUALS to ComparisonOperator.EQ,
        OperatorNames.GT to ComparisonOperator.GT,
        OperatorNames.GTE to ComparisonOperator.GTE,
        OperatorNames.LT to ComparisonOperator.LT,
        OperatorNames.LTE to ComparisonOperator.LTE,
    )
}
