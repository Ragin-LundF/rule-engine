package ruleengine.schema.dto

data class RawFieldSchema(
    val schema: String? = null,
    val normalizers: Map<String, List<String>>? = null,
    val fields: Map<String, RawFieldDefinition> = emptyMap()
)
