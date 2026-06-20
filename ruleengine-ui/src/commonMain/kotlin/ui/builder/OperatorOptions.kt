package ui.builder

/**
 * Default operator lists per field type, used when the schema does not restrict operators.
 * If the schema provides an explicit operator set, it is intersected with these defaults.
 */
object OperatorOptions {

    val TEXT: List<String> = listOf("equals", "contains", "startsWith", "endsWith", "in", "regex", "!=")
    val INTEGER: List<String> = listOf("equals", ">", ">=", "<", "<=", "between", "in", "!=")
    val DECIMAL: List<String> = listOf("equals", ">", ">=", "<", "<=", "between", "!=")
    val BOOLEAN: List<String> = listOf("equals")
    val STRING_SET: List<String> = listOf("containsAny", "containsAll")
    val DATE: List<String> = listOf("equals", ">", ">=", "<", "<=", "between")

    /**
     * Maps schema/DSL operator names (e.g. "gt", "gte") to the display symbols used in
     * [INTEGER], [DECIMAL], and [DATE] default lists (e.g. ">", ">=").
     * This allows the intersection logic to work even when the schema uses word-form names.
     */
    private val SCHEMA_NAME_TO_SYMBOL: Map<String, String> = mapOf(
        "gt" to ">",
        "gte" to ">=",
        "lt" to "<",
        "lte" to "<=",
        "ne" to "!=",
        "neq" to "!=",
        "not_equals" to "!=",
    )

    /**
     * Returns the effective operator list for a field.
     *
     * @param fieldType lowercase field type string (e.g. "text", "integer").
     * @param schemaOperators operators declared in the schema for this field; empty means "no restriction".
     */
    fun forField(fieldType: String, schemaOperators: List<String> = emptyList()): List<String> {
        val defaults = when (fieldType.lowercase()) {
            "text" -> TEXT
            "integer" -> INTEGER
            "decimal" -> DECIMAL
            "boolean" -> BOOLEAN
            "string_set" -> STRING_SET
            "date" -> DATE
            else -> TEXT
        }
        return if (schemaOperators.isEmpty()) {
            defaults
        } else {
            // Normalize schema operator names to display symbols before intersecting
            val normalizedSchema = schemaOperators.map { op -> SCHEMA_NAME_TO_SYMBOL[op] ?: op }.toSet()
            defaults.filter { it in normalizedSchema }
                .ifEmpty { schemaOperators }
        }
    }

    /** Returns true if the operator expects two values (low/high). */
    fun isBetween(operator: String): Boolean = operator == "between"

    /** Returns true if the operator expects a list of values. */
    fun isList(operator: String): Boolean = operator in listOf("in", "containsAny", "containsAll")
}
