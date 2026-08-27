package ui.schema

import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.normalizer.NormalizerRegistry
import ui.builder.OperatorOptions
import ui.schema.model.EditableField

/**
 * The lowercase spelling of a field type, used both as the `type:` value written into schema YAML
 * and as the label in the editor's type dropdown.
 *
 * Derived rather than tabulated: the visual editor used to carry its own `SchemaFieldType` enum
 * whose `yamlValue` had to equal the lowercased [FieldType] name, and the YAML bridge silently
 * degraded a field to `TEXT` whenever the two drifted. There is nothing left to drift.
 */
val FieldType.yamlValue: String
    get() = name.lowercase()

/**
 * All normalizer ids known to the engine, in declaration order, used to populate the selector and
 * the YAML completions.
 *
 * Read from [NormalizerRegistry] rather than restated: an id offered here that the registry does not
 * have is a schema the engine refuses to load.
 */
val KnownNormalizers: List<String> = NormalizerRegistry.ids.map { id -> id.value }

/**
 * Numbers and dates are ordered, so the schema editor offers them the same comparisons — plus `in`,
 * which asks about membership of a written-out set rather than about order.
 */
private val ORDERED_OPERATORS: List<String> = listOf(
    OperatorOptions.EQUALS,
    OperatorOptions.GT,
    OperatorOptions.GTE,
    OperatorOptions.LT,
    OperatorOptions.LTE,
    OperatorOptions.BETWEEN,
    OperatorOptions.IN,
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
val OperatorsByType: Map<FieldType, List<String>> = mapOf(
    FieldType.TEXT to listOf(
        OperatorOptions.EQUALS,
        OperatorOptions.CONTAINS,
        OperatorOptions.STARTS_WITH,
        OperatorOptions.ENDS_WITH,
        OperatorOptions.IN,
        OperatorOptions.REGEX,
    ),
    FieldType.INTEGER to ORDERED_OPERATORS,
    FieldType.DECIMAL to ORDERED_OPERATORS,
    FieldType.BOOLEAN to listOf(OperatorOptions.EQUALS),
    FieldType.STRING_SET to listOf(OperatorOptions.CONTAINS_ANY, OperatorOptions.CONTAINS_ALL),
    FieldType.DATE to ORDERED_OPERATORS,
    FieldType.DATE_TIME to ORDERED_OPERATORS,
    FieldType.COLLECTION to emptyList(),
    FieldType.OBJECT to emptyList(),
)

/** Operators offered for [type]. Empty for a structure type. */
fun operatorsFor(type: FieldType): List<String> {
    return OperatorsByType[type] ?: emptyList()
}

/** Every operator name the editor can offer, across all types. */
val KnownOperators: List<String> = OperatorsByType.values.flatten().distinct()

/**
 * Starting points offered by "+ Add field", one per [FieldType] plus a blank row.
 *
 * Each template's `operators` are the full set the engine allows for that type, so a freshly added field
 * is usable without editing the chips. `SchemaEditorModelsTest` asserts the list covers every type, which
 * is what keeps a newly added type from being unreachable in the menu.
 */
val FieldTemplates: List<Pair<String, EditableField>> = listOf(
    "Blank field" to EditableField(),
    "Text field" to EditableField(
        path = "field",
        type = FieldType.TEXT,
        normalizers = listOf("trim", "lowercase"),
        operators = operatorsFor(type = FieldType.TEXT),
    ),
    "Integer field" to EditableField(
        path = "count",
        type = FieldType.INTEGER,
        operators = operatorsFor(type = FieldType.INTEGER),
    ),
    "Decimal field" to EditableField(
        path = "amount",
        type = FieldType.DECIMAL,
        operators = operatorsFor(type = FieldType.DECIMAL),
    ),
    "Boolean field" to EditableField(
        path = "flag",
        type = FieldType.BOOLEAN,
        operators = operatorsFor(type = FieldType.BOOLEAN),
    ),
    "String set field (tags)" to EditableField(
        path = "tags",
        type = FieldType.STRING_SET,
        normalizers = listOf("trim", "lowercase"),
        operators = operatorsFor(type = FieldType.STRING_SET),
    ),
    // No `format`: ISO is the default, and the row's Format box shows the hint for changing it.
    "Date field" to EditableField(
        path = "bookingDate",
        type = FieldType.DATE,
        operators = operatorsFor(type = FieldType.DATE),
    ),
    "Date-time field" to EditableField(
        path = "bookedAt",
        type = FieldType.DATE_TIME,
        operators = operatorsFor(type = FieldType.DATE_TIME),
    ),
    "Collection (list of objects)" to EditableField(
        path = "items",
        type = FieldType.COLLECTION,
        fields = listOf(EditableField(path = "amount", type = FieldType.DECIMAL)),
    ),
    "Object (nested fields)" to EditableField(
        path = "customer",
        type = FieldType.OBJECT,
        fields = listOf(EditableField(path = "country", type = FieldType.TEXT)),
    ),
)
