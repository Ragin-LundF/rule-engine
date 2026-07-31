package ruleengine.compiler.operators

import ruleengine.core.domain.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.evaluator.compiled.ComparisonOperator
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.DecimalBetweenExpression
import ruleengine.evaluator.compiled.DecimalComparisonExpression
import java.math.BigDecimal

object DecimalOperator {
    @Suppress("ThrowsCount")
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        if (cond.operator.lowercase() == "between") {
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

        return when (cond.operator.lowercase()) {
            "equals", "==", "=", "eq" -> DecimalComparisonExpression(
                field = fieldId,
                expected = expected,
                op = ComparisonOperator.EQ
            )

            "gt", ">" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.GT)
            "gte", ">=" -> DecimalComparisonExpression(
                field = fieldId,
                expected = expected,
                op = ComparisonOperator.GTE
            )

            "lt", "<" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.LT)
            "lte", "<=" -> DecimalComparisonExpression(
                field = fieldId,
                expected = expected,
                op = ComparisonOperator.LTE
            )

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Unsupported operator '${cond.operator}' for decimal field"
            )
        }
    }
}
