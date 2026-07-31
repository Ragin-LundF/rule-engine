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
     * Operators allowed once either side of a comparison is a computed value. The engine's parser
     * only routes a condition through the value-expression path for symbolic operators, so these are
     * the only valid choices for a comparison row.
     */
    val COMPARISON_NUMERIC: List<String> = listOf("==", "!=", ">", ">=", "<", "<=")

    /** Text operands support equality only — the engine rejects ordering comparisons on text. */
    val COMPARISON_TEXT: List<String> = listOf("==", "!=")

    /**
     * Aggregate functions understood by the engine, lowercase.
     *
     * Must stay in sync with `ruleengine.evaluator.compiled.AggregateFunctionName`, which lives in
     * the JVM-only core module and so cannot be referenced from `commonMain`. `AggregateFunctionsTest`
     * asserts the two lists match.
     */
    val AGGREGATE_FUNCTIONS: List<String> =
        listOf("count", "sum", "subtract", "avg", "median", "max", "min")

    /** Arithmetic operators available in a calculation, in display order. */
    val ARITHMETIC_OPERATORS: List<String> = listOf("+", "-", "*", "/")

    /** Operators available inside a filter segment (`orders[...]`). */
    val FILTER_OPERATORS: List<String> = listOf("==", "!=", ">", ">=", "<", "<=")

    /** Field types that can take part in numeric comparisons and arithmetic. */
    private val NUMERIC_TYPES: Set<String> = setOf("integer", "decimal")

    /** Field types whose members are navigated with a path instead of compared directly. */
    private val STRUCTURE_TYPES: Set<String> = setOf("collection", "object")

    /** True when [fieldType] holds a number, so aggregates and arithmetic apply. */
    fun isNumericType(fieldType: String): Boolean = fieldType.lowercase() in NUMERIC_TYPES

    /** True when [fieldType] is a collection or object, i.e. navigable rather than comparable. */
    fun isStructureType(fieldType: String): Boolean = fieldType.lowercase() in STRUCTURE_TYPES

    /**
     * Comparison operators for a comparison row, given whether the operands are numeric.
     * Text operands are restricted to equality, matching the engine's validator.
     */
    fun comparisonOperators(numeric: Boolean): List<String> =
        if (numeric) COMPARISON_NUMERIC else COMPARISON_TEXT

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
            // A structure is navigated into or aggregated over, never compared directly.
            "collection", "object" -> return emptyList()
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
