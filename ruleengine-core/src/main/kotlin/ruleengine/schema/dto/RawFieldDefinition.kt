package ruleengine.schema.dto

data class RawFieldDefinition(
    val type: String? = null,
    val alias: String? = null,
    val normalizers: List<String>? = null,
    val operators: List<String>? = null
)

