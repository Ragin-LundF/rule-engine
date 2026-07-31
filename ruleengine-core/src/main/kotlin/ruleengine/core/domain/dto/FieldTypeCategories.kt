package ruleengine.core.domain.dto

/** True for the structure types whose [FieldDefinition.fields] describe nested members. */
val FieldType.isStructure: Boolean
    get() = this == FieldType.COLLECTION || this == FieldType.OBJECT

/** True for the date types that accept a [FieldDefinition.format] pattern. */
val FieldType.isTemporal: Boolean
    get() = this == FieldType.DATE || this == FieldType.DATE_TIME
