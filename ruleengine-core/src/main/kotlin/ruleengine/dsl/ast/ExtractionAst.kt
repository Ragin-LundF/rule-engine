package ruleengine.dsl.ast

/**
 * Sealed hierarchy for extraction operations in the `then` clause.
 * An extraction reads a value from a context field and transforms it
 * before it is forwarded as an action argument.
 */
sealed interface ExtractionAst {

    /**
     * Extracts a capture group from a text field using a regular expression.
     *
     * @param sourceField the name of the field in the rule context to apply the regex to
     * @param pattern     the regular expression pattern (must be a valid Java regex)
     * @param groupIndex  1-based capture-group index to extract (0 = whole match)
     */
    data class RegexExtraction(
        val sourceField: String,
        val pattern: String,
        val groupIndex: Int
    ) : ExtractionAst
}

