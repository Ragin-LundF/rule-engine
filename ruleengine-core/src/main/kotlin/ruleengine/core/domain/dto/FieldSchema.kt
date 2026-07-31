package ruleengine.core.domain.dto

data class FieldSchema(
    val name: String,
    val fields: Map<FieldId, FieldDefinition>
)
