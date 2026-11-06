package ui.schema.model

import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isMultiValued

/** True for the types `missing: empty` is legal on: they have an empty value (`[]` or `{}`). */
val FieldType.hasEmptyForm: Boolean
    get() = isMultiValued || this == FieldType.OBJECT
