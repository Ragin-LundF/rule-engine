package ui.schema

/**
 * Editable representation of a single field in the visual schema editor.
 *
 * All fields are plain strings so the composable layer stays in commonMain.
 * The bridge layer (jvmMain) converts between this model and [ruleengine.core.domain.FieldSchema].
 */
data class EditableField(
    val path: String = "",
    val alias: String = "",
    val type: SchemaFieldType = SchemaFieldType.TEXT,
    /**
     * Date pattern for a [SchemaFieldType.DATE] / [SchemaFieldType.DATE_TIME] field, e.g. `dd.MM.yyyy`.
     * Empty means ISO-8601. Always empty for every other type.
     */
    val format: String = "",
    val normalizers: List<String> = emptyList(),
    val operators: List<String> = emptyList(),
    /**
     * Members of a [SchemaFieldType.COLLECTION] or [SchemaFieldType.OBJECT] field.
     *
     * Recursive, mirroring [ruleengine.core.domain.FieldDefinition.fields], so a collection of objects
     * that themselves contain collections is expressible to any depth.
     */
    val fields: List<EditableField> = emptyList(),
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
    DATE_TIME("date_time", "date_time"),
    COLLECTION("collection", "collection"),
    OBJECT("object", "object"),
}

/** True for the types whose [EditableField.fields] describe nested members. */
val SchemaFieldType.isStructure: Boolean
    get() = this == SchemaFieldType.COLLECTION || this == SchemaFieldType.OBJECT

/** True for the date types that accept an [EditableField.format] pattern. */
val SchemaFieldType.isTemporal: Boolean
    get() = this == SchemaFieldType.DATE || this == SchemaFieldType.DATE_TIME

/**
 * True for the types whose values are normalized before comparison.
 *
 * The engine applies normalizers to text values only, so offering them elsewhere would suggest an
 * effect that never happens.
 */
val SchemaFieldType.isNormalizable: Boolean
    get() = this == SchemaFieldType.TEXT || this == SchemaFieldType.STRING_SET

/**
 * All normalizer ids known to the engine, used to populate the selector.
 *
 * Must stay in sync with `ruleengine.core.normalizer.NormalizerRegistry`, which lives in the JVM-only
 * core module and so cannot be referenced from `commonMain`. `SchemaEditorModelsTest` asserts every id
 * here is one the engine accepts.
 */
val KnownNormalizers: List<String> = listOf(
    "trim",
    "lowercase",
    "uppercase",
    "collapse_whitespace",
    "remove_punctuation",
    "german_umlaut_fold",
)

/**
 * Operators the engine allows per field type, keyed by type.
 *
 * Mirrors `Validator.supportedOperatorsFor`, which is private to the JVM-only core module. Names are the
 * canonical spellings the engine compares against — a schema that declares anything else restricts the
 * field to an operator no rule can use. `SchemaEditorModelsTest` asserts each entry against the engine.
 *
 * A structure type is navigated into or aggregated over, never compared, so it has no operators.
 */
val OperatorsByType: Map<SchemaFieldType, List<String>> = mapOf(
    SchemaFieldType.TEXT to listOf("equals", "contains", "startsWith", "endsWith", "in", "regex"),
    SchemaFieldType.INTEGER to listOf("equals", "gt", "gte", "lt", "lte", "between"),
    SchemaFieldType.DECIMAL to listOf("equals", "gt", "gte", "lt", "lte", "between"),
    SchemaFieldType.BOOLEAN to listOf("equals"),
    SchemaFieldType.STRING_SET to listOf("containsAny", "containsAll"),
    SchemaFieldType.DATE to listOf("equals", "gt", "gte", "lt", "lte", "between"),
    SchemaFieldType.DATE_TIME to listOf("equals", "gt", "gte", "lt", "lte", "between"),
    SchemaFieldType.COLLECTION to emptyList(),
    SchemaFieldType.OBJECT to emptyList(),
)

/** Operators offered for [type]. Empty for a structure type. */
fun operatorsFor(type: SchemaFieldType): List<String> {
    return OperatorsByType[type] ?: emptyList()
}

/** Every operator name the editor can offer, across all types. */
val KnownOperators: List<String> = OperatorsByType.values.flatten().distinct()

/**
 * Starting points offered by "+ Add field", one per [SchemaFieldType] plus a blank row.
 *
 * Each template's `operators` are the full set the engine allows for that type, so a freshly added field
 * is usable without editing the chips. `SchemaEditorModelsTest` asserts the list covers every type, which
 * is what keeps a newly added type from being unreachable in the menu.
 */
val FieldTemplates: List<Pair<String, EditableField>> = listOf(
    "Blank field" to EditableField(),
    "Text field" to EditableField(
        path = "field",
        type = SchemaFieldType.TEXT,
        normalizers = listOf("trim", "lowercase"),
        operators = operatorsFor(type = SchemaFieldType.TEXT),
    ),
    "Integer field" to EditableField(
        path = "count",
        type = SchemaFieldType.INTEGER,
        operators = operatorsFor(type = SchemaFieldType.INTEGER),
    ),
    "Decimal field" to EditableField(
        path = "amount",
        type = SchemaFieldType.DECIMAL,
        operators = operatorsFor(type = SchemaFieldType.DECIMAL),
    ),
    "Boolean field" to EditableField(
        path = "flag",
        type = SchemaFieldType.BOOLEAN,
        operators = operatorsFor(type = SchemaFieldType.BOOLEAN),
    ),
    "String set field (tags)" to EditableField(
        path = "tags",
        type = SchemaFieldType.STRING_SET,
        normalizers = listOf("trim", "lowercase"),
        operators = operatorsFor(type = SchemaFieldType.STRING_SET),
    ),
    // No `format`: ISO is the default, and the row's Format box shows the hint for changing it.
    "Date field" to EditableField(
        path = "bookingDate",
        type = SchemaFieldType.DATE,
        operators = operatorsFor(type = SchemaFieldType.DATE),
    ),
    "Date-time field" to EditableField(
        path = "bookedAt",
        type = SchemaFieldType.DATE_TIME,
        operators = operatorsFor(type = SchemaFieldType.DATE_TIME),
    ),
    "Collection (list of objects)" to EditableField(
        path = "items",
        type = SchemaFieldType.COLLECTION,
        fields = listOf(EditableField(path = "amount", type = SchemaFieldType.DECIMAL)),
    ),
    "Object (nested fields)" to EditableField(
        path = "customer",
        type = SchemaFieldType.OBJECT,
        fields = listOf(EditableField(path = "country", type = SchemaFieldType.TEXT)),
    ),
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
