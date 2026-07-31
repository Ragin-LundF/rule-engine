package ruleengine.compiler.operators

import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.DateBetweenExpression
import ruleengine.evaluator.compiled.DateComparisonExpression
import ruleengine.evaluator.compiled.DateComparisonOperator
import ruleengine.evaluator.context.dto.PreparedDate
import ruleengine.evaluator.context.dto.PreparedDateTime
import ruleengine.evaluator.context.dto.PreparedTemporal

/**
 * Compiles conditions on `date` and `date_time` fields.
 *
 * Literals are quoted: an ISO-8601 value (`"2024-01-31"`, `"2024-01-31T09:30:00"`), or the field's
 * declared `format` when it has one. Anything that does not parse is a load-time error rather than a
 * silent non-match.
 */
object DateOperator {

    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId, def: FieldDefinition): CompiledExpression {
        return if (cond.operator.lowercase() == "between") {
            compileBetween(ruleId = ruleId, cond = cond, fieldId = fieldId, def = def)
        } else {
            compileComparison(ruleId = ruleId, cond = cond, fieldId = fieldId, def = def)
        }
    }

    private fun compileBetween(
        ruleId: String?,
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition
    ): CompiledExpression {
        val between = cond.value as? BetweenLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Operator 'between' expects two bounds in ${expectedFormatText(def = def)} " +
                "for field '${cond.field}'"
        )
        return DateBetweenExpression(
            field = fieldId,
            low = parseLiteral(
                ruleId = ruleId,
                field = cond.field,
                def = def,
                text = between.low,
                label = "lower bound"
            ),
            high = parseLiteral(
                ruleId = ruleId,
                field = cond.field,
                def = def,
                text = between.high,
                label = "upper bound"
            ),
        )
    }

    private fun compileComparison(
        ruleId: String?,
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition
    ): CompiledExpression {
        val literal = cond.value as? StringLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected a quoted literal in ${expectedFormatText(def = def)} " +
                "for ${typeName(def = def)} field '${cond.field}'"
        )
        val operator = OPERATORS[cond.operator.lowercase()] ?: throw CompilationException(
            ruleId = ruleId,
            details = "Unsupported operator '${cond.operator}' for ${typeName(def = def)} field '${cond.field}'"
        )
        return DateComparisonExpression(
            field = fieldId,
            expected = parseLiteral(
                ruleId = ruleId,
                field = cond.field,
                def = def,
                text = literal.value,
                label = "date"
            ),
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

    /** Reads a literal in the field's own format, or reports where the bad value came from. */
    private fun parseLiteral(
        ruleId: String?,
        field: String,
        def: FieldDefinition,
        text: String,
        label: String
    ): PreparedTemporal<*> {
        val parsed = temporalOf(def = def, text = text)
        return parsed ?: throw CompilationException(
            ruleId = ruleId,
            details = "Invalid $label '$text' for ${typeName(def = def)} field '$field'; " +
                "expected ${expectedFormatText(def = def)}"
        )
    }

    /** True when [text] can be read as a value of [def]. Used by the validator for early feedback. */
    fun isValidLiteral(text: String, def: FieldDefinition): Boolean {
        return temporalOf(def = def, text = text) != null
    }

    /** How a literal for [def] must be written, for use in error messages and hints. */
    fun expectedFormatText(def: FieldDefinition): String {
        val pattern = def.format
        if (pattern != null) {
            return "format '$pattern' (e.g. \"${TemporalFormat.sample(type = def.type, pattern = pattern)}\")"
        }
        return if (def.type == FieldType.DATE_TIME) {
            "ISO format YYYY-MM-DDTHH:MM:SS"
        } else {
            "ISO format YYYY-MM-DD"
        }
    }

    private fun temporalOf(def: FieldDefinition, text: String): PreparedTemporal<*>? {
        if (def.type == FieldType.DATE_TIME) {
            return TemporalFormat.parseDateTime(text = text, pattern = def.format)?.let { PreparedDateTime(value = it) }
        }
        return TemporalFormat.parseDate(text = text, pattern = def.format)?.let { PreparedDate(value = it) }
    }

    private fun typeName(def: FieldDefinition): String {
        return def.type.name.lowercase()
    }
}
