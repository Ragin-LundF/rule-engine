package ui.util

/**
 * Writing a string as a YAML scalar.
 *
 * Shared by the manifest and action-schema bridges, which serialise YAML by hand. It is deliberately
 * *not* shared with the rule-DSL writer: the two escape rules happen to coincide today, but they
 * belong to different languages and the DSL's rule is the core `Lexer`'s to define.
 */
object YamlScalars {

    private val INDICATOR_CHARS: Set<Char> = setOf(
        '-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%', '@', '`',
    )

    /** Escapes the characters that cannot appear raw inside a double-quoted YAML scalar. */
    fun escape(value: String): String {
        return value
            .replace(oldValue = "\\", newValue = "\\\\")
            .replace(oldValue = "\"", newValue = "\\\"")
    }

    /**
     * [value] as a YAML scalar, quoted only when it has to be.
     *
     * Quoting is avoided where it is safe to, because an unquoted manifest reads better; it becomes
     * necessary when the value is empty, has significant surrounding whitespace, opens with a YAML
     * indicator character, or contains a `: ` / ` #` sequence a parser would read as structure.
     */
    fun quoteIfNeeded(value: String): String {
        val needsQuotes = value.isEmpty() ||
                value.first().isWhitespace() ||
                value.last().isWhitespace() ||
                value.first() in INDICATOR_CHARS ||
                value.contains(other = ": ") ||
                value.contains(other = " #") ||
                value.endsWith(suffix = ":")

        if (!needsQuotes) {
            return value
        }

        return "\"" + escape(value = value) + "\""
    }
}
