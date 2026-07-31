package ui.autocompletion

import ruleengine.core.domain.FieldType

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
internal val SET_OPS     = listOf("contains", "containsAny", "containsAll")
internal val DATE_OPS    = listOf("equals", "gt", "gte", "lt", "lte", "between")

internal fun defaultOperatorsForType(fieldType: FieldType): List<String> {
    return when (fieldType) {
        FieldType.TEXT       -> TEXT_OPS
        FieldType.INTEGER    -> NUM_OPS
        FieldType.DECIMAL    -> NUM_OPS
        FieldType.BOOLEAN    -> BOOL_OPS
        FieldType.STRING_SET -> SET_OPS
        FieldType.DATE       -> DATE_OPS
        // Structures are navigated, not compared: no direct operators.
        FieldType.COLLECTION, FieldType.OBJECT -> emptyList()
    }
}

internal fun valuePlaceholderForOperator(op: String, fieldType: FieldType): String {
    return when (op.lowercase()) {
        "between"                    -> "0 100"
        "in", "containsany", "containsall" -> "[\"a\", \"b\"]"
        else                         -> when (fieldType) {
            FieldType.TEXT       -> "\"value\""
            FieldType.INTEGER    -> "0"
            FieldType.DECIMAL    -> "0.0"
            FieldType.BOOLEAN    -> "true"
            FieldType.STRING_SET -> "\"value\""
            FieldType.DATE       -> "\"2024-01-01\""
            FieldType.COLLECTION, FieldType.OBJECT -> ""
        }
    }
}

