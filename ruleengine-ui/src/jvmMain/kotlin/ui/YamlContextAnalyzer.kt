package ui


// Where the caret is inside a YAML document — which key it is under, whether it is on a value
// or a list item, and at what indent. Feeds the completion side; the highlighter tracks its own
// nesting as it walks lines, so the two do not share this.

// ── YAML cursor context analyzer ──────────────────────────────────────────────

/**
 * Analyzes the YAML [text] to determine the editing context at [cursorPos].
 * Uses indent-level heuristics rather than a full YAML parse.
 */
fun analyzeYamlContext(text: String, cursorPos: Int): YamlCursorContext {
    return runCatching {
        val safePos = cursorPos.coerceIn(0, text.length)

        // Locate the current line.
        val lineStart = text.lastIndexOf('\n', safePos - 1) + 1
        val currentLine = text.substring(startIndex = lineStart, endIndex = safePos)
        val trimmedCurrentLine = currentLine.trimStart()
        val currentIndent = currentLine.length - trimmedCurrentLine.length

        val isListItem = trimmedCurrentLine.startsWith("- ") || trimmedCurrentLine == "-"
        val isValue = !isListItem && trimmedCurrentLine.contains(':')

        val currentKey = if (isValue) {
            extractKey(trimmedCurrentLine)
        } else {
            null
        }

        // Scan backwards to find the enclosing parent key (first line with less indent).
        val parentKey = findParentKey(text = text, lineStart = lineStart, childIndent = currentIndent)

        YamlCursorContext(
            currentKey = currentKey,
            parentKey = parentKey,
            isValue = isValue,
            isListItem = isListItem,
            currentIndent = currentIndent,
        )
    }.getOrElse {
        YamlCursorContext()
    }
}

/** Extracts the key name from a trimmed `key: value` or `key:` line. */
private fun extractKey(trimmedLine: String): String? {
    val colonIdx = trimmedLine.indexOf(':')
    return if (colonIdx > 0) trimmedLine.substring(0, colonIdx).trim() else null
}

/** Scans previous lines to find the closest ancestor key with less indent than [childIndent]. */
private fun findParentKey(text: String, lineStart: Int, childIndent: Int): String? {
    if (lineStart == 0) return null
    val previousContent = text.substring(0, lineStart)
    val prevLines = previousContent.lines()
    for (line in prevLines.asReversed()) {
        if (line.isBlank()) continue
        val trimmed = line.trimStart()
        val lineIndent = line.length - trimmed.length
        if (lineIndent < childIndent && trimmed.contains(char = ':')) {
            return extractKey(trimmedLine = trimmed)
        }
    }
    return null
}
