package ruleengine.compiler.operators

import ruleengine.core.domain.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.DateBetweenExpression
import ruleengine.evaluator.compiled.DateComparisonExpression
import ruleengine.evaluator.compiled.DateComparisonOperator
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Compiles conditions on `date` fields.
 *
 * Date literals are quoted ISO-8601 calendar dates (`"2024-01-31"`). Anything that does not parse is
 * a load-time error rather than a silent non-match.
 */
object DateOperator {

    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression =
        if (cond.operator.lowercase() == "between") {
            compileBetween(ruleId = ruleId, cond = cond, fieldId = fieldId)
        } else {
            compileComparison(ruleId = ruleId, cond = cond, fieldId = fieldId)
        }

    private fun compileBetween(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        val between = cond.value as? BetweenLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Operator 'between' expects two ISO date bounds for field '${cond.field}'"
        )
        return DateBetweenExpression(
            field = fieldId,
            low = parseDate(ruleId = ruleId, field = cond.field, text = between.low, label = "lower bound"),
            high = parseDate(ruleId = ruleId, field = cond.field, text = between.high, label = "upper bound"),
        )
    }

    private fun compileComparison(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        val literal = cond.value as? StringLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected a quoted ISO date literal for date field '${cond.field}'"
        )
        val operator = OPERATORS[cond.operator.lowercase()] ?: throw CompilationException(
            ruleId = ruleId,
            details = "Unsupported operator '${cond.operator}' for date field '${cond.field}'"
        )
        return DateComparisonExpression(
            field = fieldId,
            expected = parseDate(ruleId = ruleId, field = cond.field, text = literal.value, label = "date"),
            op = operator
        )
    }

    private val OPERATORS: Map<String, DateComparisonOperator> = mapOf(
        "equals" to DateComparisonOperator.EQ,
        "==" to DateComparisonOperator.EQ,
        "=" to DateComparisonOperator.EQ,
        "eq" to DateComparisonOperator.EQ,
        "gt" to DateComparisonOperator.GT,
        ">" to DateComparisonOperator.GT,
        "gte" to DateComparisonOperator.GTE,
        ">=" to DateComparisonOperator.GTE,
        "lt" to DateComparisonOperator.LT,
        "<" to DateComparisonOperator.LT,
        "lte" to DateComparisonOperator.LTE,
        "<=" to DateComparisonOperator.LTE,
    )

    /** Parses an ISO-8601 calendar date, or reports where the bad value came from. */
    fun parseDate(ruleId: String?, field: String, text: String, label: String): LocalDate =
        try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            throw CompilationException(
                ruleId = ruleId,
                details = "Invalid $label '$text' for date field '$field'; expected ISO format YYYY-MM-DD"
            )
        }

    /** True when [text] is a valid ISO-8601 calendar date. Used by the validator for early feedback. */
    fun isIsoDate(text: String): Boolean =
        try {
            LocalDate.parse(text)
            true
        } catch (_: DateTimeParseException) {
            false
        }
}
