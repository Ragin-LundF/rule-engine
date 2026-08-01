package ui.builder

/**
 * The operator vocabulary for the visual editor: the names themselves, and the default list offered
 * per field type when the schema does not restrict them.
 *
 * Mirrors `ruleengine.core.domain.OperatorNames`, which is the engine's authority. It has to be a
 * mirror rather than a reference: this file is in `commonMain`, and the core module is JVM-only, so
 * `commonMain` cannot see it. `OperatorOptionsTest` asserts the two agree — the same arrangement
 * [AGGREGATE_FUNCTIONS] already uses for the engine's aggregate enum.
 *
 * Every other file in the UI takes its operator names from here rather than spelling them again.
 */
object OperatorOptions {

    // ── operator names, mirroring ruleengine.core.domain.OperatorNames ────────

    const val EQUALS = "equals"
    const val GT = "gt"
    const val GTE = "gte"
    const val LT = "lt"
    const val LTE = "lte"
    const val BETWEEN = "between"
    const val CONTAINS = "contains"
    const val STARTS_WITH = "startsWith"
    const val ENDS_WITH = "endsWith"
    const val IN = "in"
    const val CONTAINS_ANY = "containsAny"
    const val CONTAINS_ALL = "containsAll"
    const val REGEX = "regex"

    const val SYMBOL_EQUALS = "=="
    const val SYMBOL_NOT_EQUALS = "!="
    const val SYMBOL_GT = ">"
    const val SYMBOL_GTE = ">="
    const val SYMBOL_LT = "<"
    const val SYMBOL_LTE = "<="

    /** The canonical names, in the order the engine documents them. */
    val ALL: List<String> = listOf(
        EQUALS, GT, GTE, LT, LTE, BETWEEN,
        CONTAINS, STARTS_WITH, ENDS_WITH, IN,
        CONTAINS_ANY, CONTAINS_ALL, REGEX,
    )

    // ── defaults per field type ───────────────────────────────────────────────

    val TEXT: List<String> = listOf(EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, IN, REGEX, SYMBOL_NOT_EQUALS)

    // No `in`: the engine allows it on text fields only (`Validator.supportedOperatorsFor`).
    val INTEGER: List<String> =
        listOf(EQUALS, SYMBOL_GT, SYMBOL_GTE, SYMBOL_LT, SYMBOL_LTE, BETWEEN, SYMBOL_NOT_EQUALS)

    val DECIMAL: List<String> = INTEGER
    val BOOLEAN: List<String> = listOf(EQUALS)
    val STRING_SET: List<String> = listOf(CONTAINS_ANY, CONTAINS_ALL)
    val DATE: List<String> = listOf(EQUALS, SYMBOL_GT, SYMBOL_GTE, SYMBOL_LT, SYMBOL_LTE, BETWEEN)

    /**
     * Operators allowed once either side of a comparison is a computed value. The engine's parser
     * only routes a condition through the value-expression path for symbolic operators, so these are
     * the only valid choices for a comparison row.
     */
    val COMPARISON_NUMERIC: List<String> =
        listOf(SYMBOL_EQUALS, SYMBOL_NOT_EQUALS, SYMBOL_GT, SYMBOL_GTE, SYMBOL_LT, SYMBOL_LTE)

    /** Text operands support equality only — the engine rejects ordering comparisons on text. */
    val COMPARISON_TEXT: List<String> = listOf(SYMBOL_EQUALS, SYMBOL_NOT_EQUALS)

    /**
     * Aggregate functions understood by the engine, lowercase.
     *
     * Must stay in sync with `ruleengine.evaluator.compiled.AggregateFunctionName`, which lives in
     * the JVM-only core module and so cannot be referenced from `commonMain`. `OperatorOptionsTest`
     * asserts the two lists match.
     */
    val AGGREGATE_FUNCTIONS: List<String> =
        listOf("count", "sum", "subtract", "avg", "median", "max", "min")

    /** Arithmetic operators available in a calculation, in display order. */
    val ARITHMETIC_OPERATORS: List<String> = listOf("+", "-", "*", "/")

    /** Operators available inside a filter segment (`orders[...]`). */
    val FILTER_OPERATORS: List<String> = COMPARISON_NUMERIC

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
        GT to SYMBOL_GT,
        GTE to SYMBOL_GTE,
        LT to SYMBOL_LT,
        LTE to SYMBOL_LTE,
        // The engine has no named inequality, but schemas written by hand sometimes declare one.
        "ne" to SYMBOL_NOT_EQUALS,
        "neq" to SYMBOL_NOT_EQUALS,
        "not_equals" to SYMBOL_NOT_EQUALS,
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
            "date", "date_time" -> DATE
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
    fun isBetween(operator: String): Boolean = operator == BETWEEN

    /** Returns true if the operator expects a list of values. */
    fun isList(operator: String): Boolean = operator in listOf(IN, CONTAINS_ANY, CONTAINS_ALL)
}
