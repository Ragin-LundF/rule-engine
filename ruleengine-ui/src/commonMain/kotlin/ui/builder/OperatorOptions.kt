package ui.builder

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isStructure
import ruleengine.evaluator.compiled.DslFunctions

/**
 * The operator vocabulary for the visual editor: the names themselves, and the default list offered
 * per field type when the schema does not restrict them.
 *
 * The names are re-exported from [OperatorNames] rather than restated, so there is one spelling of
 * each operator in the repository and the compiler enforces it. This object stays the UI's single
 * entry point for them: every other file in the UI takes its operator names from here.
 *
 * What is *not* shared is the per-type default lists below. They are a presentation choice — which
 * operators to offer in a dropdown — and deliberately differ from the engine's
 * `Validator.supportedOperatorsFor`, which decides what is *legal*. See the note on [forField].
 */
object OperatorOptions {

    // ── operator names, re-exported from the engine's authority ───────────────

    const val EQUALS = OperatorNames.EQUALS
    const val GT = OperatorNames.GT
    const val GTE = OperatorNames.GTE
    const val LT = OperatorNames.LT
    const val LTE = OperatorNames.LTE
    const val BETWEEN = OperatorNames.BETWEEN
    const val CONTAINS = OperatorNames.CONTAINS
    const val STARTS_WITH = OperatorNames.STARTS_WITH
    const val ENDS_WITH = OperatorNames.ENDS_WITH
    const val IN = OperatorNames.IN
    const val CONTAINS_ANY = OperatorNames.CONTAINS_ANY
    const val CONTAINS_ALL = OperatorNames.CONTAINS_ALL
    const val REGEX = OperatorNames.REGEX

    const val SYMBOL_EQUALS = OperatorNames.SYMBOL_EQUALS
    const val SYMBOL_NOT_EQUALS = OperatorNames.SYMBOL_NOT_EQUALS
    const val SYMBOL_GT = OperatorNames.SYMBOL_GT
    const val SYMBOL_GTE = OperatorNames.SYMBOL_GTE
    const val SYMBOL_LT = OperatorNames.SYMBOL_LT
    const val SYMBOL_LTE = OperatorNames.SYMBOL_LTE

    /** The canonical names, in the order the engine documents them. */
    val ALL: List<String> = OperatorNames.ALL

    // ── defaults per field type ───────────────────────────────────────────────

    val TEXT: List<String> = listOf(EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, IN, REGEX, SYMBOL_NOT_EQUALS)

    val INTEGER: List<String> =
        listOf(EQUALS, SYMBOL_GT, SYMBOL_GTE, SYMBOL_LT, SYMBOL_LTE, BETWEEN, IN, SYMBOL_NOT_EQUALS)

    val DECIMAL: List<String> = INTEGER
    val BOOLEAN: List<String> = listOf(EQUALS)
    val STRING_SET: List<String> = listOf(CONTAINS_ANY, CONTAINS_ALL)
    val DATE: List<String> = listOf(EQUALS, SYMBOL_GT, SYMBOL_GTE, SYMBOL_LT, SYMBOL_LTE, BETWEEN, IN)

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
     * The only operator valid against a list variable.
     *
     * Deliberately not folded into [COMPARISON_NUMERIC]: `contains` on an aggregate or a plain field
     * comparison is either a validation error or a condition that can never match.
     */
    val LIST_VARIABLE: List<String> = listOf(CONTAINS)

    /**
     * Aggregate functions understood by the engine, in declaration order.
     *
     * The reductions only. A function such as `daysBetween` is a valid value expression but not a
     * reduction over a collection, so offering it in a picker that then asks for a collection path
     * would produce a rule the validator rejects. [ALL_FUNCTIONS] is the list for surfaces that must
     * recognise everything the parser accepts.
     */
    val AGGREGATE_FUNCTIONS: List<String> = DslFunctions.aggregateNames()

    /** Every function name the DSL accepts — for highlighting and completion, not for pickers. */
    val ALL_FUNCTIONS: List<String> = DslFunctions.allNames()

    /** Arithmetic operators available in a calculation, in display order. */
    val ARITHMETIC_OPERATORS: List<String> = listOf("+", "-", "*", "/")

    /**
     * Operators available inside a filter segment (`orders[...]`).
     *
     * `in` is the one non-symbolic member: it tests an element's member against a list, a string set
     * or another collection, which no symbolic operator expresses with the operands in that order.
     */
    val FILTER_OPERATORS: List<String> = COMPARISON_NUMERIC + IN

    /**
     * Catalog type of a rule output variable whose value type could not be inferred from its `set`
     * expression — a field path, or another variable.
     *
     * Not a schema field type: the engine types a variable at evaluation time and its validator
     * accepts every comparison operator on one, so the editor must not narrow the choice either.
     */
    const val VARIABLE_TYPE: String = "variable"

    /**
     * Catalog type of a rule output variable built by `add` clauses, i.e. a list.
     *
     * Distinct from [VARIABLE_TYPE] because the two offer opposite operator sets: an untyped variable
     * takes any symbolic comparison and no `contains`, a list takes `contains` and nothing else. Not a
     * schema field type — no field can be declared with it.
     */
    const val LIST_VARIABLE_TYPE: String = "list_variable"

    /** Field types that can take part in numeric comparisons and arithmetic. */
    private val NUMERIC_TYPES: Set<String> = setOf("integer", "decimal")

    /** Field types whose members are navigated with a path instead of compared directly. */
    private val STRUCTURE_TYPES: Set<String> =
        FieldType.entries.filter { type -> type.isStructure }.map { type -> type.name.lowercase() }.toSet()

    /** True when [fieldType] holds a number, so aggregates and arithmetic apply. */
    fun isNumericType(fieldType: String): Boolean = fieldType.lowercase() in NUMERIC_TYPES

    /** True when [fieldType] is a collection or object, i.e. navigable rather than comparable. */
    fun isStructureType(fieldType: String): Boolean = fieldType.lowercase() in STRUCTURE_TYPES

    /** Field types that hold more than one element, so `sortBy` has something to put in order. */
    private val ORDERABLE_TYPES: Set<String> = setOf(
        FieldType.COLLECTION.name.lowercase(),
        FieldType.STRING_SET.name.lowercase(),
    )

    /**
     * True when [fieldType] can be ordered by `sortBy`.
     *
     * Wider than [isStructureType] on purpose, and narrower in the other direction: a `string_set`
     * is ordered by its own values, and an `object` holds one thing and has no order. This mirrors
     * what `ValueExpressionValidator.validateSort` accepts, so the Builder cannot offer an ordering
     * the engine then rejects.
     */
    fun isOrderableType(fieldType: String): Boolean = fieldType.lowercase() in ORDERABLE_TYPES

    /**
     * True when [fieldType] holds text.
     *
     * Used by the extraction row: `Validator` rejects a regex extraction whose source field is not
     * TEXT, so offering anything else would be offering a choice that cannot validate.
     */
    fun isTextType(fieldType: String): Boolean = fieldType.lowercase() == TEXT_TYPE

    private const val TEXT_TYPE: String = "text"

    /** True when [fieldType] is an untyped rule output variable — see [VARIABLE_TYPE]. */
    fun isVariableType(fieldType: String): Boolean = fieldType.lowercase() == VARIABLE_TYPE

    /** True when [fieldType] is a list-valued rule output variable — see [LIST_VARIABLE_TYPE]. */
    fun isListVariableType(fieldType: String): Boolean = fieldType.lowercase() == LIST_VARIABLE_TYPE

    /** True when a catalog id names a rule output variable rather than a schema field. */
    fun isVariableId(fieldId: String): Boolean = fieldId.startsWith(prefix = "$")

    /**
     * Operators for a catalog entry, which may be a schema field or a rule output variable.
     *
     * A variable takes only the spellings the parser routes through the expression path. A named
     * operator would be read as a plain field comparison, and `${'$'}name` is not a field — the rule would
     * be rejected with "unknown field". Which spellings apply depends on what the variable *holds*,
     * not on the value type guessed from its `set` expression: a `set tier = 2` is catalogued as
     * `decimal` so a comparison row can offer ordering, and `decimal` would otherwise bring `equals`
     * and `between` with it.
     */
    fun forCatalogField(
        fieldId: String,
        fieldType: String,
        schemaOperators: List<String> = emptyList(),
    ): List<String> {
        if (isVariableId(fieldId = fieldId)) {
            return if (isListVariableType(fieldType = fieldType)) LIST_VARIABLE else COMPARISON_NUMERIC
        }
        return forField(fieldType = fieldType, schemaOperators = schemaOperators)
    }

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
     * Known divergence, deliberately left alone: the defaults here are not the same set as the
     * engine's `Validator.supportedOperatorsFor`, nor as `ui.schema.SchemaEditorModels.operatorsFor`
     * or `ui.autocompletion.defaultOperatorsForType`. A `date` field offers `>` here but `gt` in
     * autocomplete, and `text` offers `!=` here but not in the schema editor. Collapsing the four
     * onto one source would change what each surface shows, so it needs a product decision rather
     * than a refactor. Both this and `supportedOperatorsFor` also fall back silently on an unknown
     * type — here to [TEXT], there to the empty set.
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
            // An untyped variable takes any symbolic comparison; the engine checks neither side.
            VARIABLE_TYPE -> return COMPARISON_NUMERIC
            // A list is only ever tested for membership. Ordering and equality against a whole list
            // always evaluate to false, so offering them would only produce rules that never match.
            LIST_VARIABLE_TYPE -> return LIST_VARIABLE
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
