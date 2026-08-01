package ruleengine.compiler.operators

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.numeric.ComparisonOperator
import ruleengine.evaluator.compiled.numeric.DecimalBetweenExpression
import ruleengine.evaluator.compiled.numeric.DecimalComparisonExpression
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

    /** Keyed by canonical name only — the caller normalises every alias before it gets here. */
    private val COMPARISONS: Map<String, ComparisonOperator> = mapOf(
        OperatorNames.EQUALS to ComparisonOperator.EQ,
        OperatorNames.GT to ComparisonOperator.GT,
        OperatorNames.GTE to ComparisonOperator.GTE,
        OperatorNames.LT to ComparisonOperator.LT,
        OperatorNames.LTE to ComparisonOperator.LTE,
    )
}
