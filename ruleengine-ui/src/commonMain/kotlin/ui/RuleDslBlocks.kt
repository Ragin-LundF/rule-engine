package ui

/**
 * Locating and replacing one rule's block inside a `.rule` buffer.
 *
 * Kept out of the editor screen because it is pure text work with no Compose in it, which is also
 * what makes it testable: `RuleBlockReplacementTest` drives it directly. Same package as before, so
 * that test needed no change.
 */

/**
 * Replaces the DSL block for [ruleId] inside [fullText] with [newRuleDsl].
 * If the rule block is not found, appends [newRuleDsl] at the end.
 *
 * The body is located by counting braces rather than by a `[^}]*` match, because a rule body may
 * legitimately contain `}` — most often inside a regex pattern such as `regex "^DE\\d{20}$"`. A
 * regex-based match fails on those rules, and a failed match silently appends a duplicate instead of
 * replacing the original.
 */
internal fun replaceRuleDslBlock(fullText: String, ruleId: String, newRuleDsl: String): String {
    val range = findRuleBlockRange(fullText = fullText, ruleId = ruleId)
        ?: return if (fullText.isBlank()) newRuleDsl else "$fullText\n$newRuleDsl"
    return fullText.replaceRange(range = range, replacement = newRuleDsl)
}

/**
 * Finds the character range of the `rule "<id>" { ... }` block, or null when there is none.
 * Braces inside string literals and `#` comments are ignored.
 */
internal fun findRuleBlockRange(fullText: String, ruleId: String): IntRange? {
    val escapedId = Regex.escape(literal = ruleId)
    val header = Regex(pattern = """rule\s+"$escapedId"\s*\{""")
    val match = header.find(input = fullText) ?: return null

    var depth = 0
    var index = match.range.last // positioned on the opening brace
    var inString = false
    var inComment = false

    while (index < fullText.length) {
        val char = fullText[index]
        when {
            inComment -> if (char == '\n') inComment = false
            inString -> when (char) {
                '\\' -> index++ // skip the escaped character
                '"' -> inString = false
            }
            char == '"' -> inString = true
            char == '#' -> inComment = true
            char == '{' -> depth++
            char == '}' -> {
                depth--
                if (depth == 0) return match.range.first..index
            }
        }
        index++
    }
    // Unbalanced braces: treat the rule as not found rather than corrupting the text.
    return null
}
