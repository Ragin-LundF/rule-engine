package ruleengine.core.domain

@JvmInline
value class FieldId(val value: String)

@JvmInline
value class OperatorId(val value: String)

@JvmInline
value class NormalizerId(val value: String)

enum class FieldType {
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    STRING_SET,
    DATE,

    /** A calendar date with a time of day. Unlike [DATE], the time component is kept and compared. */
    DATE_TIME,

    /** A list of elements, navigable with dotted paths and filters (e.g. `orders[status == "paid"].total`). */
    COLLECTION,

    /** A single nested object, navigable with dotted paths (e.g. `customer.address.city`). */
    OBJECT
}

/** True for the structure types whose [FieldDefinition.fields] describe nested members. */
val FieldType.isStructure: Boolean
    get() = this == FieldType.COLLECTION || this == FieldType.OBJECT

/** True for the date types that accept a [FieldDefinition.format] pattern. */
val FieldType.isTemporal: Boolean
    get() = this == FieldType.DATE || this == FieldType.DATE_TIME

data class FieldDefinition(
    val id: FieldId,
    val type: FieldType,
    val alias: String? = null,
    /**
     * Date pattern for a [FieldType.DATE] / [FieldType.DATE_TIME] field, e.g. `dd.MM.yyyy`.
     *
     * Governs both how a `String` input value is read and how a literal in a rule must be written.
     * `null` means ISO-8601, which is the default. Always `null` for every other field type.
     */
    val format: String? = null,
    val normalizers: List<NormalizerId> = emptyList(),
    val operators: Set<OperatorId> = emptySet(),
    /**
     * Nested members of a [FieldType.COLLECTION] or [FieldType.OBJECT] field.
     *
     * Recursive: a nested member may itself be a structure with its own [fields], so nesting depth
     * is unbounded. Empty for scalar fields, and also empty for structures whose members are not
     * declared — in that case path validation stays permissive (see `ValueExpressionValidator`).
     */
    val fields: Map<FieldId, FieldDefinition> = emptyMap()
)

data class FieldSchema(
    val name: String,
    val fields: Map<FieldId, FieldDefinition>
)

data class RuleAction(
    val name: String,
    val arguments: List<Any?> = emptyList()
)

data class RuleMatch(
    val ruleId: String,
    val actions: List<RuleAction>
)

data class EvaluationResult(
    val matches: List<RuleMatch>,
    val trace: Any? = null
)

