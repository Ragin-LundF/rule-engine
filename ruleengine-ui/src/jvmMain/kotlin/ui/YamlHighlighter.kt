package ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

// ── YAML editor type ──────────────────────────────────────────────────────────

enum class YamlEditorType { FIELD_SCHEMA, ACTION_SCHEMA }

// ── YAML cursor context for completions ───────────────────────────────────────

/**
 * Describes what the cursor is "inside" within a YAML document.
 *
 * @property currentKey    The key whose value is being edited (e.g., `"type"`, `"schema"`).
 * @property parentKey     The enclosing key (e.g., `"purpose"`, `"normalizers"`, `"operators"`).
 * @property isValue       True when the cursor is on the value side of a `key: ` line.
 * @property isListItem    True when the cursor is on a `- ` list item line.
 * @property currentIndent The number of leading spaces on the cursor's line.
 */
data class YamlCursorContext(
    val currentKey: String? = null,
    val parentKey: String? = null,
    val isValue: Boolean = false,
    val isListItem: Boolean = false,
    val currentIndent: Int = 0,
)

// ── YAML completion candidates ────────────────────────────────────────────────

private val FIELD_TYPE_VALUES   = listOf("text", "integer", "decimal", "boolean", "stringSet", "date")
private val NORMALIZER_VALUES   = listOf(
    "trim", "lowercase", "uppercase",
    "german_umlaut_fold", "ascii_fold",
    "collapse_whitespace", "remove_punctuation",
)
private val OPERATOR_VALUES     = listOf(
    "equals", "contains", "startsWith", "endsWith", "in", "regex",
    "gt", "gte", "lt", "lte", "between",
    "containsAny", "containsAll",
)
private val ARG_TYPE_VALUES     = listOf("string", "integer", "decimal")

private val FIELD_SCHEMA_TOP_KEYS   = listOf("schema", "fields")
private val ACTION_SCHEMA_TOP_KEYS  = listOf("actions")
private val FIELD_DEF_KEYS          = listOf("type", "normalizers", "operators")
private val ACTION_DEF_KEYS         = listOf("argTypes")

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
        if (lineIndent < childIndent && trimmed.contains(':')) {
            return extractKey(trimmed)
        }
    }
    return null
}

// ── YAML completion builder ───────────────────────────────────────────────────

/**
 * Returns context-appropriate YAML completion items for [context] and [editorType].
 */
fun buildYamlCompletions(
    context: YamlCursorContext,
    editorType: YamlEditorType,
): List<CompletionItem> {
    return when {
        // Value of `type:` → field type values
        context.isValue && context.currentKey == "type" ->
            FIELD_TYPE_VALUES.map { value ->
                CompletionItem(label = value, insertText = value, kind = CompletionKind.LITERAL, hint = "field type")
            }

        // List item under `normalizers:` → normalizer names
        context.isListItem && context.parentKey == "normalizers" ->
            NORMALIZER_VALUES.map { value ->
                CompletionItem(label = value, insertText = value, kind = CompletionKind.LITERAL, hint = "normalizer")
            }

        // List item under `operators:` → operator names
        context.isListItem && context.parentKey == "operators" ->
            OPERATOR_VALUES.map { value ->
                CompletionItem(label = value, insertText = value, kind = CompletionKind.OPERATOR, hint = "operator")
            }

        // List item under `argTypes:` → argument type names
        context.isListItem && context.parentKey == "argTypes" ->
            ARG_TYPE_VALUES.map { value ->
                CompletionItem(label = value, insertText = value, kind = CompletionKind.LITERAL, hint = "arg type")
            }

        // Keys at indent 4 (sub-keys of a field definition)
        context.currentIndent == 4 && editorType == YamlEditorType.FIELD_SCHEMA ->
            FIELD_DEF_KEYS.map { key ->
                CompletionItem(label = key, insertText = "$key:", kind = CompletionKind.KEYWORD, hint = "field property")
            }

        // Keys at indent 4 (sub-keys of an action definition)
        context.currentIndent == 4 && editorType == YamlEditorType.ACTION_SCHEMA ->
            ACTION_DEF_KEYS.map { key ->
                CompletionItem(label = key, insertText = "$key: []", kind = CompletionKind.KEYWORD, hint = "action property")
            }

        // Top-level keys for field schema
        context.currentIndent == 0 && editorType == YamlEditorType.FIELD_SCHEMA ->
            FIELD_SCHEMA_TOP_KEYS.map { key ->
                CompletionItem(label = key, insertText = "$key: ", kind = CompletionKind.KEYWORD, hint = "schema key")
            }

        // Top-level keys for action schema
        context.currentIndent == 0 && editorType == YamlEditorType.ACTION_SCHEMA ->
            ACTION_SCHEMA_TOP_KEYS.map { key ->
                CompletionItem(label = key, insertText = "$key:", kind = CompletionKind.KEYWORD, hint = "schema key")
            }

        else -> emptyList()
    }
}

// ── YAML syntax highlighter ───────────────────────────────────────────────────

/**
 * Builds an [AnnotatedString] with syntax colouring for a YAML field-schema or action-schema file.
 *
 * Uses a line-by-line approach with an indent-tracking context stack to determine how
 * each token should be coloured without requiring a full YAML parser.
 */
fun annotateYaml(text: String, editorType: YamlEditorType): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)

        val lines = text.split('\n')
        var lineOffset = 0

        // Context stack: (indentLevel, key) — the key at that indent level.
        val contextStack = ArrayDeque<Pair<Int, String>>()

        for (line in lines) {
            val lineLen = line.length
            val trimmed = line.trimStart()
            val indent  = lineLen - trimmed.length

            // Pop all stack frames whose indent is >= current line indent.
            // This handles "de-indenting" back to a shallower level.
            while (contextStack.isNotEmpty() && contextStack.last().first >= indent) {
                contextStack.removeLast()
            }

        val parentEntry = contextStack.lastOrNull()
        val parentKey   = parentEntry?.second

            when {
                trimmed.startsWith('#') -> {
                    // Comment — entire line is muted and italic.
                    addStyle(
                        style = SpanStyle(color = TextMuted, fontStyle = FontStyle.Italic),
                        start = lineOffset,
                        end   = lineOffset + lineLen,
                    )
                }

                trimmed.startsWith("- ") || trimmed == "-" -> {
                    // List item.
                    val dashAbs  = lineOffset + indent
                    val valueAbs = dashAbs + 2 // skip "- "
                    val valueStr = if (trimmed.length > 2) trimmed.substring(2).trimEnd() else ""

                    // Colour the dash punctuation.
                    addStyle(style = SpanStyle(color = TextMuted), start = dashAbs, end = dashAbs + 1)

                    // Colour the list value depending on the enclosing key.
                    val valueStyle = resolveListItemStyle(parentKey = parentKey)
                    if (valueStr.isNotEmpty() && valueAbs < lineOffset + lineLen) {
                        addStyle(style = valueStyle, start = valueAbs, end = lineOffset + lineLen)
                    }

                    // Inline bracket list items like `argTypes: [string]` are handled by the
                    // key-value branch below; plain `- value` items are handled here.
                }

                trimmed.contains(':') -> {
                    // Key-value line (or key-only line like `fields:`).
                    val colonIdx   = trimmed.indexOf(':')
                    val key        = trimmed.substring(0, colonIdx).trim()
                    val valueRaw   = trimmed.substring(colonIdx + 1)
                    val valueTrimmed = valueRaw.trim()

                    val keyAbsStart = lineOffset + indent
                    val keyAbsEnd   = keyAbsStart + key.length
                    val colonAbs    = keyAbsEnd

                    // Colour the key.
                    val keyStyle = resolveKeyStyle(
                        key = key,
                        indent = indent,
                        parentKey = parentKey,
                        editorType = editorType,
                    )
                    addStyle(style = keyStyle, start = keyAbsStart, end = keyAbsEnd)

                    // Colour the colon separator.
                    addStyle(style = SpanStyle(color = TextMuted), start = colonAbs, end = colonAbs + 1)

                    // Colour the inline value (if present and not a nested block).
                    if (valueTrimmed.isNotEmpty()) {
                        applyValueStyle(
                            key = key,
                            value = valueTrimmed,
                            lineOffset = lineOffset,
                            line = line,
                            colonIdx = indent + colonIdx,
                        )
                    }

                    // Push the current key onto the context stack so child lines inherit it.
                    contextStack.addLast(Pair(indent, key))
                }
            }

            // Advance to the next line (+1 for the '\n' separator).
            lineOffset += lineLen + 1
        }
    }
}

/** Returns the [SpanStyle] for a YAML key based on its structural position. */
private fun resolveKeyStyle(
    key: String,
    indent: Int,
    parentKey: String?,
    @Suppress("UNUSED_PARAMETER") editorType: YamlEditorType,
): SpanStyle {
    return when {
        // Top-level structural keys (`schema`, `fields`, `actions`).
        indent == 0 -> SpanStyle(color = ColorKeyword, fontWeight = FontWeight.SemiBold)

        // Field names are at indent 2 under `fields` (including aliases).
        indent == 2 && parentKey == "fields" -> SpanStyle(color = ColorField)

        // Action names are at indent 2 under `actions`.
        indent == 2 && parentKey == "actions" -> SpanStyle(color = ColorAction)

        // Sub-keys of field definitions (`type`, `normalizers`, `operators`, `alias`).
        key in setOf("type", "normalizers", "operators", "alias") ->
            SpanStyle(color = ColorKeyword)

        else -> SpanStyle(color = TextSecondary)
    }
}

/** Returns the [SpanStyle] for a YAML list item value based on the enclosing key. */
private fun resolveListItemStyle(parentKey: String?): SpanStyle {
    return when (parentKey) {
        "normalizers" -> SpanStyle(color = AccentOrange)
        "operators"   -> SpanStyle(color = ColorOp)
        "argTypes"    -> SpanStyle(color = ColorNumber)
        else          -> SpanStyle(color = TextPrimary)
    }
}

/**
 * Applies a span style to the inline value part of a `key: value` YAML line.
 * Handles inline bracket lists (e.g., `argTypes: [string]`) by colouring the items.
 */
private fun AnnotatedString.Builder.applyValueStyle(
    key: String,
    value: String,
    lineOffset: Int,
    line: String,
    colonIdx: Int,
) {
    // Find where the value starts in the original line (first non-space after the colon).
    val valueStartInLine = line.indexOf(value.trimStart(), startIndex = colonIdx + 1)
        .coerceAtLeast(colonIdx + 1)
    val valueAbsStart = lineOffset + valueStartInLine

    if (value.startsWith('[') && value.endsWith(']')) {
        // Inline list: colour each element individually.
        colorInlineListItems(
            bracketContent = value.substring(1, value.length - 1),
            bracketAbsStart = valueAbsStart + 1,
            key = key,
        )
    } else {
        val valueStyle = when (key) {
            "type"   -> fieldTypeValueColor(value)
            "schema" -> SpanStyle(color = ColorString)
            else     -> SpanStyle(color = TextPrimary)
        }
        addStyle(style = valueStyle, start = valueAbsStart, end = valueAbsStart + value.length)
    }
}

/** Colours the items inside an inline bracket list like `[string, integer]`. */
private fun AnnotatedString.Builder.colorInlineListItems(
    bracketContent: String,
    bracketAbsStart: Int,
    key: String,
) {
    val itemStyle = when (key) {
        "argTypes"    -> SpanStyle(color = ColorNumber)
        "normalizers" -> SpanStyle(color = AccentOrange)
        "operators"   -> SpanStyle(color = ColorOp)
        else          -> SpanStyle(color = TextPrimary)
    }
    var scanOffset = 0
    bracketContent.split(',').forEach { rawItem ->
        val item = rawItem.trim()
        if (item.isNotEmpty()) {
            val itemStart = bracketContent.indexOf(item, startIndex = scanOffset)
            if (itemStart >= 0) {
                addStyle(
                    style = itemStyle,
                    start = bracketAbsStart + itemStart,
                    end   = bracketAbsStart + itemStart + item.length,
                )
                scanOffset = itemStart + item.length
            }
        }
    }
}

/** Returns a [SpanStyle] whose colour represents the YAML `type:` field value. */
private fun fieldTypeValueColor(value: String): SpanStyle {
    val color: Color = when (value.lowercase().trim()) {
        "text", "string"               -> Color(0xFF79C0FF)
        "integer", "int", "long"       -> Color(0xFF58A6FF)
        "decimal", "bigdecimal", "number" -> Color(0xFF58A6FF)
        "boolean", "bool"              -> AccentPurple
        "stringset", "string_set", "set" -> AccentGreen
        "date"                         -> AccentOrange
        else                           -> TextPrimary
    }
    return SpanStyle(color = color)
}



