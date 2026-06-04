package ruleengine.schema.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RawFieldSchema(
    val schema: String? = null,
    val normalizers: Map<String, List<String>>? = null,
    val fields: Map<String, RawFieldDefinition> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RawFieldDefinition(
    val type: String? = null,
    val normalizers: List<String>? = null,
    val operators: List<String>? = null
)

