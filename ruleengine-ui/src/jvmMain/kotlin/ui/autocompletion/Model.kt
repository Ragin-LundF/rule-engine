package ui.autocompletion

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isTemporal

/**
 * Numbers and dates are ordered, so both accept the same named comparisons — plus `in`, which is
 * about membership of a written-out set rather than about order.
 */
internal val ORDERED_OPS = listOf(
    OperatorNames.EQUALS,
    OperatorNames.GT,
    OperatorNames.GTE,
    OperatorNames.LT,
    OperatorNames.LTE,
    OperatorNames.BETWEEN,
    OperatorNames.IN,
)

internal val TEXT_OPS = listOf(
    OperatorNames.EQUALS,
    OperatorNames.CONTAINS,
    OperatorNames.STARTS_WITH,
    OperatorNames.ENDS_WITH,
    OperatorNames.IN,
    OperatorNames.REGEX,
    OperatorNames.SYMBOL_EQUALS,
    OperatorNames.SYMBOL_NOT_EQUALS,
)

internal val NUM_OPS = ORDERED_OPS + listOf(
    OperatorNames.SYMBOL_EQUALS,
    OperatorNames.SYMBOL_NOT_EQUALS,
    OperatorNames.SYMBOL_GT,
    OperatorNames.SYMBOL_GTE,
    OperatorNames.SYMBOL_LT,
    OperatorNames.SYMBOL_LTE,
)

internal val BOOL_OPS = listOf(OperatorNames.EQUALS)

// A string set is tested for membership only; the engine rejects `contains` on it.
internal val SET_OPS = listOf(OperatorNames.CONTAINS_ANY, OperatorNames.CONTAINS_ALL)

internal val DATE_OPS = ORDERED_OPS

internal fun defaultOperatorsForType(fieldType: FieldType): List<String> {
    return when (fieldType) {
        FieldType.TEXT       -> TEXT_OPS
        FieldType.INTEGER    -> NUM_OPS
        FieldType.DECIMAL    -> NUM_OPS
        FieldType.BOOLEAN    -> BOOL_OPS
        FieldType.STRING_SET -> SET_OPS
        FieldType.DATE, FieldType.DATE_TIME -> DATE_OPS
        // Structures are navigated, not compared: no direct operators.
        FieldType.COLLECTION, FieldType.OBJECT -> emptyList()
    }
}

/**
 * Snippet inserted after an operator, in the shape the field actually accepts — so a date field with a
 * declared `format` suggests a value in that format rather than an ISO one the compiler would reject.
 */
internal fun valuePlaceholderForOperator(op: String, def: FieldDefinition): String {
    if (OperatorUtils.normalizeOperator(op = op) == OperatorNames.BETWEEN) {
        // Date bounds are quoted values, not numbers, so they need their own sample pair.
        if (def.type.isTemporal) {
            val sample = temporalSample(def = def)
            return "$sample $sample"
        }
        return "0 100"
    }
    if (OperatorUtils.normalizeOperator(op = op) == OperatorNames.IN) {
        return membershipPlaceholder(def = def)
    }
    return when (op.lowercase()) {
        OperatorNames.CONTAINS_ANY, OperatorNames.CONTAINS_ALL -> "[\"a\", \"b\"]"
        else                         -> when (def.type) {
            FieldType.TEXT       -> "\"value\""
            FieldType.INTEGER    -> "0"
            FieldType.DECIMAL    -> "0.0"
            FieldType.BOOLEAN    -> "true"
            FieldType.STRING_SET -> "\"value\""
            FieldType.DATE, FieldType.DATE_TIME -> temporalSample(def = def)
            FieldType.COLLECTION, FieldType.OBJECT -> ""
        }
    }
}

/**
 * The list `in` inserts, typed like the field.
 *
 * Type-aware for the same reason the `between` branch is: `in` reaches numeric and temporal fields
 * now, and the one hardcoded `["a", "b"]` it used to insert would complete to a list the validator
 * rejects on any of them.
 */
private fun membershipPlaceholder(def: FieldDefinition): String {
    val sample = when (def.type) {
        FieldType.INTEGER -> "0" to "100"
        FieldType.DECIMAL -> "0.0" to "1.0"
        FieldType.DATE, FieldType.DATE_TIME -> temporalSample(def = def) to temporalSample(def = def)
        else -> "\"a\"" to "\"b\""
    }
    return "[${sample.first}, ${sample.second}]"
}

private fun temporalSample(def: FieldDefinition): String {
    return "\"${TemporalFormat.sample(type = def.type, pattern = def.format)}\""
}
