package ruleengine.core.domain

/**
 * Every operator name the engine understands, in one place.
 *
 * The names are shared vocabulary: the parser reads them, the validator checks a field's declared
 * `operators:` list against them, the compiler dispatches on them, the trace labels them, the export
 * renders them as prose and the visual editor offers them. Spelled as literals at each of those
 * sites they drift — one place writes `startsWith`, another `startswith` — and the mismatch shows up
 * only as a rule that silently fails to compile.
 *
 * [CANONICAL] is the spelling every stage after [ruleengine.compiler.operators.OperatorUtils]
 * normalisation sees. The alias constants exist so the alias table itself can be written without
 * loose strings; nothing downstream of normalisation should need them.
 */
object OperatorNames {

    // ── canonical names ───────────────────────────────────────────────────────

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

    // ── symbolic and legacy spellings ─────────────────────────────────────────

    const val SYMBOL_EQUALS = "=="
    const val SYMBOL_ASSIGN_EQUALS = "="
    const val SHORT_EQUALS = "eq"
    const val SYMBOL_NOT_EQUALS = "!="
    const val SYMBOL_GT = ">"
    const val SYMBOL_GTE = ">="
    const val SYMBOL_LT = "<"
    const val SYMBOL_LTE = "<="

    /**
     * `snake_case` and all-lowercase spellings written by earlier versions of the visual schema
     * editor. Recognising them keeps those schemas working — without the alias, a `starts_with`
     * declaration would restrict the field to a name no condition can ever match.
     */
    const val LOWERCASE_STARTS_WITH = "startswith"
    const val SNAKE_STARTS_WITH = "starts_with"
    const val LOWERCASE_ENDS_WITH = "endswith"
    const val SNAKE_ENDS_WITH = "ends_with"
    const val LOWERCASE_CONTAINS_ANY = "containsany"
    const val LOWERCASE_CONTAINS_ALL = "containsall"
    const val MATCHES = "matches"
    const val REGEXP = "regexp"

    /** The canonical names, for callers that enumerate rather than match. */
    val ALL: List<String> = listOf(
        EQUALS,
        GT,
        GTE,
        LT,
        LTE,
        BETWEEN,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        IN,
        CONTAINS_ANY,
        CONTAINS_ALL,
        REGEX,
    )

    /**
     * Every spelling mapped to its canonical name, keyed in lowercase.
     *
     * Consumed by [ruleengine.compiler.operators.OperatorUtils.normalizeOperator], which is the only
     * thing that should read it.
     */
    val CANONICAL: Map<String, String> = mapOf(
        SYMBOL_EQUALS to EQUALS,
        EQUALS to EQUALS,
        SYMBOL_ASSIGN_EQUALS to EQUALS,
        SHORT_EQUALS to EQUALS,
        SYMBOL_GTE to GTE,
        GTE to GTE,
        SYMBOL_GT to GT,
        GT to GT,
        SYMBOL_LTE to LTE,
        LTE to LTE,
        SYMBOL_LT to LT,
        LT to LT,
        CONTAINS to CONTAINS,
        LOWERCASE_STARTS_WITH to STARTS_WITH,
        SNAKE_STARTS_WITH to STARTS_WITH,
        LOWERCASE_ENDS_WITH to ENDS_WITH,
        SNAKE_ENDS_WITH to ENDS_WITH,
        IN to IN,
        LOWERCASE_CONTAINS_ANY to CONTAINS_ANY,
        LOWERCASE_CONTAINS_ALL to CONTAINS_ALL,
        REGEX to REGEX,
        MATCHES to REGEX,
        REGEXP to REGEX,
        BETWEEN to BETWEEN,
    )
}
