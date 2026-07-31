package ruleengine.compiler.operators

import ruleengine.core.domain.dto.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.IntegerBetweenExpression
import ruleengine.evaluator.compiled.IntegerComparisonExpression
import ruleengine.evaluator.compiled.IntegerComparisonOperator

object IntegerOperator {
    @Suppress("LongMethod", "ThrowsCount")
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        if (cond.operator.lowercase() == "between") {
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

        return when (cond.operator.lowercase()) {
            "equals", "==", "=", "eq" -> IntegerComparisonExpression(
                field = fieldId,
                expected = expected,
                op = IntegerComparisonOperator.EQ
            )

            "gt", ">" -> IntegerComparisonExpression(
                field = fieldId,
                expected = expected,
                op = IntegerComparisonOperator.GT
            )

            "gte", ">=" -> IntegerComparisonExpression(
                field = fieldId,
                expected = expected,
                op = IntegerComparisonOperator.GTE
            )

            "lt", "<" -> IntegerComparisonExpression(
                field = fieldId,
                expected = expected,
                op = IntegerComparisonOperator.LT
            )

            "lte", "<=" -> IntegerComparisonExpression(
                field = fieldId,
                expected = expected,
                op = IntegerComparisonOperator.LTE
            )

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Unsupported operator '${cond.operator}' for integer field"
            )
        }
    }
}

