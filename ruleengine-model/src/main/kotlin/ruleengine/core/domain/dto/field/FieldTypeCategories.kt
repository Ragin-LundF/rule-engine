package ruleengine.core.domain.dto.field

/** True for the structure types whose [FieldDefinition.fields] describe nested members. */
val FieldType.isStructure: Boolean
    get() = this == FieldType.COLLECTION || this == FieldType.OBJECT

/** True for the date types that accept a [FieldDefinition.format] pattern. */
val FieldType.isTemporal: Boolean
    get() = this == FieldType.DATE || this == FieldType.DATE_TIME

/**
 * True for the types whose values are normalized before comparison.
 *
 * The engine applies normalizers to text values only, so declaring them elsewhere would suggest an
 * effect that never happens.
 */
val FieldType.isNormalizable: Boolean
    get() = this == FieldType.TEXT || this == FieldType.STRING_SET

/**
 * True for the types that hold more than one value.
 *
 * What makes `contains` a membership test rather than a substring test. A path reaching one of these
 * is collection-valued however many elements the record happens to carry, which is the distinction the
 * runtime type alone cannot make: a selection of exactly one element arrives as a scalar.
 */
val FieldType.isMultiValued: Boolean
    get() = this == FieldType.COLLECTION || this == FieldType.STRING_SET
