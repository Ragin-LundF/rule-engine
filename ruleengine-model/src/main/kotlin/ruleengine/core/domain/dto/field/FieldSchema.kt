package ruleengine.core.domain.dto.field

data class FieldSchema(
    val name: String,
    val fields: Map<FieldId, FieldDefinition>
)
