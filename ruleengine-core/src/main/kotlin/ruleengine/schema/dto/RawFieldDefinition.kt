package ruleengine.schema.dto

data class RawFieldDefinition(
    val type: String? = null,
    val alias: String? = null,
    /** Date pattern for a `date` / `date_time` field, e.g. `dd.MM.yyyy`. */
    val format: String? = null,
    val normalizers: List<String>? = null,
    val operators: List<String>? = null,
    /** Nested members of a `collection` / `object` field. Recursive, so nesting depth is unbounded. */
    val fields: Map<String, RawFieldDefinition>? = null
)

