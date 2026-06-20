package ui.schema

/**
 * Editable representation of a single field in the visual schema editor.
 *
 * All fields are plain strings so the composable layer stays in commonMain.
 * The bridge layer (jvmMain) converts between this model and [FieldSchema].
 */
data class EditableField(
    val path: String = "",
    val alias: String = "",
    val type: SchemaFieldType = SchemaFieldType.TEXT,
    val normalizers: List<String> = emptyList(),
    val operators: List<String> = emptyList(),
)

/**
 * Supported field types exposed in the visual editor.
 * Maps 1-to-1 with [ruleengine.core.domain.FieldType].
 */
enum class SchemaFieldType(val displayName: String, val yamlValue: String) {
    TEXT("text", "text"),
    INTEGER("integer", "integer"),
    DECIMAL("decimal", "decimal"),
    BOOLEAN("boolean", "boolean"),
    STRING_SET("string_set", "string_set"),
    DATE("date", "date"),
}

/** All normalizer ids known to the engine, used to populate the selector. */
val KnownNormalizers: List<String> = listOf(
    "trim",
    "lowercase",
    "uppercase",
    "german_umlaut_fold",
)

/** All operator ids known to the engine, used to populate the selector. */
val KnownOperators: List<String> = listOf(
    "equals",
    "not_equals",
    "contains",
    "not_contains",
    "starts_with",
    "ends_with",
    "gt",
    "gte",
    "lt",
    "lte",
    "between",
    "in",
    "containsAny",
    "containsAll",
    "isEmpty",
    "isNotEmpty",
)

/**
 * Immutable snapshot of the visual schema editor state.
 *
 * @param schemaName  Name of the schema (maps to the `schema:` YAML key).
 * @param fields      Ordered list of editable field rows.
 * @param isReadOnly  True when the loaded YAML contains unsupported constructs
 *                    (e.g. custom normalizer groups); editing is disabled.
 */
data class SchemaEditorState(
    val schemaName: String = "",
    val fields: List<EditableField> = emptyList(),
    val isReadOnly: Boolean = false,
) {
    companion object {
        val Empty = SchemaEditorState()
    }
}
