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

/**
 * The id of the rule whose `rule "<id>" { ... }` block contains [caret], or null when the caret sits
 * between rules — in a blank line, a file-level comment, or past the last block.
 *
 * Blocks never overlap, so the first containing block is the only one. Locating them with
 * [findRuleBlockRange] rather than with the parser is deliberate: this runs on every caret move, and
 * a buffer mid-edit is routinely unparseable while its text still says plainly which rule the caret
 * is in.
 */
internal fun ruleIdAtCaret(fullText: String, ruleIds: List<String>, caret: Int): String? {
    if (fullText.isEmpty() || ruleIds.isEmpty()) return null
    val position = caret.coerceIn(minimumValue = 0, maximumValue = fullText.length)
    return ruleIds.firstOrNull { ruleId ->
        val range = findRuleBlockRange(fullText = fullText, ruleId = ruleId)
        range != null && position in range
    }
}

/**
 * The 1-based line range spanned by [ruleId]'s block, or null when there is no such block.
 *
 * Used to scope diagnostics to one rule: a `ValidationDiagnostic` carries a line, and a rule
 * inspector that showed every diagnostic in the buffer would attribute its neighbours' errors to the
 * rule on screen.
 */
internal fun ruleLineRange(fullText: String, ruleId: String): IntRange? {
    val range = findRuleBlockRange(fullText = fullText, ruleId = ruleId) ?: return null
    val firstLine = fullText.lineCountUpTo(offset = range.first)
    val lastLine = firstLine + fullText.substring(range.first, range.last + 1).count { it == '\n' }
    return firstLine..lastLine
}

/** 1-based line number of [offset]. */
private fun String.lineCountUpTo(offset: Int): Int {
    var lines = 1
    for (index in 0 until offset.coerceAtMost(length)) {
        if (this[index] == '\n') lines++
    }
    return lines
}
