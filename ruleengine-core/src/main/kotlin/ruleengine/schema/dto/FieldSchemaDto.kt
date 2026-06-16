package ruleengine.schema.dto

// We intentionally avoid using Jackson-specific annotations here because the global
// ObjectMapper is configured to ignore unknown properties. Keeping these DTOs
// plain makes them compatible across Jackson versions.
data class RawFieldSchema(
    val schema: String? = null,
    val normalizers: Map<String, List<String>>? = null,
    val fields: Map<String, RawFieldDefinition> = emptyMap()
)

data class RawFieldDefinition(
    val type: String? = null,
    val alias: String? = null,
    val normalizers: List<String>? = null,
    val operators: List<String>? = null
)

