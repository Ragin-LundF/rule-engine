package ruleengine.compiler.operators

import ruleengine.compiler.operators.DateOperator.parseLiteral
import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.condition.ConditionAst
import ruleengine.dsl.ast.literal.BetweenLiteral
import ruleengine.dsl.ast.literal.ListLiteral
import ruleengine.dsl.ast.literal.StringLiteral
import ruleengine.dsl.ast.path.FieldSegmentAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.temporal.DateBetweenExpression
import ruleengine.evaluator.compiled.temporal.DateComparisonExpression
import ruleengine.evaluator.compiled.temporal.DateComparisonOperator
import ruleengine.evaluator.compiled.temporal.DateInExpression
import ruleengine.evaluator.compiled.value.result.DateExpressionValue
import ruleengine.evaluator.context.dto.PreparedDate
import ruleengine.evaluator.context.dto.PreparedDateTime
import ruleengine.evaluator.context.dto.PreparedTemporal

/**
 * Compiles conditions on `date` and `date_time` fields.
 *
 * Literals are quoted: an ISO-8601 value (`"2024-01-31"`, `"2024-01-31T09:30:00"`), or the field's
 * declared `format` when it has one. A date-only literal on an ISO `date_time` field compares by day.
 * Anything that does not parse is a load-time error rather than a silent non-match.
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
        return when (op) {
            OperatorNames.BETWEEN -> compileBetween(ruleId = ruleId, cond = cond, fieldId = fieldId, def = def)
            OperatorNames.IN -> compileMembership(ruleId = ruleId, cond = cond, fieldId = fieldId, def = def)
            else -> compileComparison(ruleId = ruleId, cond = cond, fieldId = fieldId, def = def, op = op)
        }
    }

    /**
     * `field in ["2024-01-01", "2024-07-01"]`.
     *
     * Every item goes through the same [parseLiteral] the other branches use, so the list is read
     * under the field's declared `format` and a bad item names itself the way a bad bound does. A bare
     * literal counts as a set of one, matching how `TextInOperator` reads `status in "paid"`.
     */
    private fun compileMembership(
        ruleId: String?,
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
    ): CompiledExpression {
        val items = when (val literal = cond.value) {
            is ListLiteral -> literal.items
            is StringLiteral -> listOf(literal)
            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'in' expects a list of quoted values in " +
                    "${expectedFormatText(def = def)} for ${typeName(def = def)} field '${cond.field}'"
            )
        }
        val expected = items.mapTo(mutableSetOf()) { item ->
            val text = (item as? StringLiteral)?.value ?: throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'in' expects quoted values in ${expectedFormatText(def = def)} " +
                    "for ${typeName(def = def)} field '${cond.field}'"
            )
            parseLiteral(ruleId = ruleId, field = cond.field, def = def, text = text, label = "date")
        }
        return DateInExpression(field = fieldId, expected = expected)
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

    /**
     * [text] read as a value of [def] for the value-expression path, or null when it does not parse.
     *
     * `bookedAt == "2024-06-15T09:30:00"` is the symbolic spelling of `bookedAt equals …`; reading the
     * literal under the field's declaration here is what makes both agree on a `date_time` and on a
     * field with a `format`.
     */
    fun literalValueOrNull(text: String, def: FieldDefinition): DateExpressionValue? {
        return when (val parsed = temporalOf(def = def, text = text)) {
            is PreparedDateTime -> DateExpressionValue(
                value = parsed.value.toLocalDate(),
                timeOfDay = parsed.value.toLocalTime()
            )

            is PreparedDate -> DateExpressionValue(value = parsed.value)
            null -> null
        }
    }

    /**
     * The declaration of a plain path to a `date` / `date_time` field, or null for anything else.
     *
     * Decides where a text literal on the value-expression path (`dueDate == "31.01.2024"`) is read
     * as a date: the compiler reads it there, and the validator rejects one that does not parse.
     */
    fun declaredTemporalField(expr: ValueExpressionAst, schema: FieldSchema): FieldDefinition? {
        val path = (expr as? FieldAccessAst)?.path ?: return null
        if (path.any { segment -> segment !is FieldSegmentAst }) {
            return null
        }
        val name = path.joinToString(separator = ".") { segment -> (segment as FieldSegmentAst).name }
        val resolution = FieldPathResolver.resolve(identifier = name, schema = schema)
        val definition = (resolution as? FieldPathResolution.Resolved)?.definition ?: return null
        return definition.takeIf { def -> def.type == FieldType.DATE || def.type == FieldType.DATE_TIME }
    }

    /** How a literal for [def] must be written, for use in error messages and hints. */
    fun expectedFormatText(def: FieldDefinition): String {
        val pattern = def.format
        if (pattern != null) {
            return "format '$pattern' (e.g. \"${TemporalFormat.sample(type = def.type, pattern = pattern)}\")"
        }
        return if (def.type == FieldType.DATE_TIME) {
            "ISO format YYYY-MM-DDTHH:MM:SS or YYYY-MM-DD"
        } else {
            "ISO format YYYY-MM-DD"
        }
    }

    /**
     * On a `date_time` field without a `format`, a date-only ISO literal (`"2024-06-15"`) is a plain day:
     * every operator then compares by calendar day. A declared `format` is the only spelling it accepts.
     */
    private fun temporalOf(def: FieldDefinition, text: String): PreparedTemporal<*>? {
        if (def.type == FieldType.DATE_TIME) {
            val dateTime = TemporalFormat.parseDateTime(text = text, pattern = def.format)
            if (dateTime != null) {
                return PreparedDateTime(value = dateTime)
            }
            if (def.format != null) {
                return null
            }
            return TemporalFormat.parseDate(text = text, pattern = null)?.let { date -> PreparedDate(value = date) }
        }
        return TemporalFormat.parseDate(text = text, pattern = def.format)?.let { PreparedDate(value = it) }
    }

    private fun typeName(def: FieldDefinition): String {
        return def.type.name.lowercase()
    }
}
