package ruleengine.compiler.operators

import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.core.domain.FieldId
import ruleengine.evaluator.compiled.*

object DecimalOperator {
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        if (cond.operator.lowercase() == "between") {
            val between = cond.value as? BetweenLiteral ?: throw CompilationException(ruleId, "Operator 'between' expects two numeric bounds for field '${cond.field}'")
            val low = runCatching { java.math.BigDecimal(between.low) }.getOrElse { ex -> throw CompilationException(ruleId, "Invalid lower bound: ${between.low}") }
            val high = runCatching { java.math.BigDecimal(between.high) }.getOrElse { ex -> throw CompilationException(ruleId, "Invalid upper bound: ${between.high}") }
            return DecimalBetweenExpression(field = fieldId, low = low, high = high)
        }

        val literal = cond.value as? NumberLiteral ?: throw CompilationException(ruleId, "Expected numeric literal for decimal field '${cond.field}'")
        val expected = runCatching { java.math.BigDecimal(literal.value) }.getOrElse { ex -> throw CompilationException(ruleId, "Invalid decimal literal: ${literal.value}") }

        return when (cond.operator.lowercase()) {
            "equals", "==", "=" , "eq" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.EQ)
            "gt", ">" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.GT)
            "gte", ">=" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.GTE)
            "lt", "<" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.LT)
            "lte", "<=" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.LTE)
            else -> throw CompilationException(ruleId, "Unsupported operator '${cond.operator}' for decimal field")
        }
    }
}

object IntegerOperator {
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        if (cond.operator.lowercase() == "between") {
            val between = cond.value as? BetweenLiteral ?: throw CompilationException(ruleId, "Operator 'between' expects two integer bounds for field '${cond.field}'")
            val low = runCatching { between.low.toLong() }.getOrElse { ex -> throw CompilationException(ruleId, "Invalid lower bound: ${between.low}") }
            val high = runCatching { between.high.toLong() }.getOrElse { ex -> throw CompilationException(ruleId, "Invalid upper bound: ${between.high}") }
            return IntegerBetweenExpression(field = fieldId, low = low, high = high)
        }

        val literal = cond.value as? NumberLiteral ?: throw CompilationException(ruleId, "Expected numeric literal for integer field '${cond.field}'")
        val expected = runCatching { literal.value.toLong() }.getOrElse { ex -> throw CompilationException(ruleId, "Invalid integer literal: ${literal.value}") }

        return when (cond.operator.lowercase()) {
            "equals", "==", "=", "eq" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.EQ)
            "gt", ">" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.GT)
            "gte", ">=" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.GTE)
            "lt", "<" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.LT)
            "lte", "<=" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.LTE)
            else -> throw CompilationException(ruleId, "Unsupported operator '${cond.operator}' for integer field")
        }
    }
}

