package ui.autocompletion

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.isTemporal

// ── Completion model

public enum class CompletionKind { KEYWORD, LOGIC, FIELD, ACTION, LITERAL, OPERATOR }

public data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val hint: String = "",
)

// Export some small helpers that builder needs
internal val TEXT_OPS    = listOf("equals", "contains", "startsWith", "endsWith", "in", "regex", "==", "!=")
internal val NUM_OPS     = listOf("equals", "gt", "gte", "lt", "lte", "between", "==", "!=", ">", ">=", "<", "<=")
internal val BOOL_OPS    = listOf("equals")
// A string set is tested for membership only; the engine rejects `contains` on it.
internal val SET_OPS     = listOf("containsAny", "containsAll")
internal val DATE_OPS    = listOf("equals", "gt", "gte", "lt", "lte", "between")

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
    if (op.lowercase() == "between") {
        // Date bounds are quoted values, not numbers, so they need their own sample pair.
        if (def.type.isTemporal) {
            val sample = temporalSample(def = def)
            return "$sample $sample"
        }
        return "0 100"
    }
    return when (op.lowercase()) {
        "in", "containsany", "containsall" -> "[\"a\", \"b\"]"
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

private fun temporalSample(def: FieldDefinition): String {
    return "\"${TemporalFormat.sample(type = def.type, pattern = def.format)}\""
}
