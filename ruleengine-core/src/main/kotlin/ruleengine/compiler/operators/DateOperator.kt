package ruleengine.compiler.operators

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.temporal.DateBetweenExpression
import ruleengine.evaluator.compiled.temporal.DateComparisonExpression
import ruleengine.evaluator.compiled.temporal.DateComparisonOperator
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

    /** [op] is the canonical operator name; aliases are already resolved by the caller. */
    fun compile(
        ruleId: String?,
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        op: String,
    ): CompiledExpression {
        return if (op == OperatorNames.BETWEEN) {
            compileBetween(ruleId = ruleId, cond = cond, fieldId = fieldId, def = def)
        } else {
            compileComparison(ruleId = ruleId, cond = cond, fieldId = fieldId, def = def, op = op)
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
        def: FieldDefinition,
        op: String,
    ): CompiledExpression {
        val literal = cond.value as? StringLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected a quoted literal in ${expectedFormatText(def = def)} " +
                "for ${typeName(def = def)} field '${cond.field}'"
        )
        val operator = OPERATORS[op] ?: throw CompilationException(
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

    /** Keyed by canonical name only — the caller normalises every alias before it gets here. */
    private val OPERATORS: Map<String, DateComparisonOperator> = mapOf(
        OperatorNames.EQUALS to DateComparisonOperator.EQ,
        OperatorNames.GT to DateComparisonOperator.GT,
        OperatorNames.GTE to DateComparisonOperator.GTE,
        OperatorNames.LT to DateComparisonOperator.LT,
        OperatorNames.LTE to DateComparisonOperator.LTE,
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
