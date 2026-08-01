package ruleengine.core.domain.dto

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
