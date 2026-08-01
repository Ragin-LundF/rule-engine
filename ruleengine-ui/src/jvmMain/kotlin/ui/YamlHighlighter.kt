package ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

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
        var lineOffset = 0
        append(text)
        // Context stack: (indentLevel, key) — the key at that indent level. Frames at or deeper than
        // the current line are popped first, which is how a de-indent returns to the right parent.
        val contextStack = ArrayDeque<Pair<Int, String>>()

        for (line in text.split('\n')) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            while (contextStack.isNotEmpty() && contextStack.last().first >= indent) {
                contextStack.removeLast()
            }
            styleLine(
                line = line,
                trimmed = trimmed,
                indent = indent,
                lineOffset = lineOffset,
                parentKey = contextStack.lastOrNull()?.second,
                editorType = editorType,
                contextStack = contextStack,
            )
            lineOffset += line.length + 1 // +1 for the '\n' separator
        }
    }
}

@Suppress("LongParameterList")
private fun AnnotatedString.Builder.styleLine(
    line: String,
    trimmed: String,
    indent: Int,
    lineOffset: Int,
    parentKey: String?,
    editorType: YamlEditorType,
    contextStack: ArrayDeque<Pair<Int, String>>,
) {
    when {
        trimmed.startsWith('#') -> addStyle(
            style = SpanStyle(color = TextMuted, fontStyle = FontStyle.Italic),
            start = lineOffset,
            end = lineOffset + line.length,
        )

        trimmed.startsWith("- ") || trimmed == "-" ->
            styleListItem(
                lineLen = line.length,
                trimmed = trimmed,
                indent = indent,
                lineOffset = lineOffset,
                parentKey = parentKey,
            )

        trimmed.contains(':') -> {
            val key = styleKeyValue(
                line = line,
                trimmed = trimmed,
                indent = indent,
                lineOffset = lineOffset,
                parentKey = parentKey,
                editorType = editorType,
            )
            // Push the key so child lines inherit it as their parent.
            contextStack.addLast(Pair(indent, key))
        }
    }
}

/**
 * A `- value` item.
 *
 * Inline bracket lists such as `argTypes: [string]` are not this shape — they arrive at the
 * key-value branch instead.
 */
private fun AnnotatedString.Builder.styleListItem(
    lineLen: Int,
    trimmed: String,
    indent: Int,
    lineOffset: Int,
    parentKey: String?,
) {
    val dashAbs = lineOffset + indent
    val valueAbs = dashAbs + 2 // skip "- "
    val valueStr = if (trimmed.length > 2) trimmed.substring(2).trimEnd() else ""

    addStyle(style = SpanStyle(color = TextMuted), start = dashAbs, end = dashAbs + 1)

    val valueStyle = resolveListItemStyle(parentKey = parentKey)
    if (valueStr.isNotEmpty() && valueAbs < lineOffset + lineLen) {
        addStyle(style = valueStyle, start = valueAbs, end = lineOffset + lineLen)
    }
}

/** A `key: value` line, or a key-only line such as `fields:`. Returns the key it styled. */
@Suppress("LongParameterList")
private fun AnnotatedString.Builder.styleKeyValue(
    line: String,
    trimmed: String,
    indent: Int,
    lineOffset: Int,
    parentKey: String?,
    editorType: YamlEditorType,
): String {
    val colonIdx = trimmed.indexOf(':')
    val key = trimmed.substring(0, colonIdx).trim()
    val valueTrimmed = trimmed.substring(colonIdx + 1).trim()

    val keyAbsStart = lineOffset + indent
    val keyAbsEnd = keyAbsStart + key.length

    addStyle(
        style = resolveKeyStyle(key = key, indent = indent, parentKey = parentKey, editorType = editorType),
        start = keyAbsStart,
        end = keyAbsEnd,
    )
    addStyle(style = SpanStyle(color = TextMuted), start = keyAbsEnd, end = keyAbsEnd + 1)

    if (valueTrimmed.isNotEmpty()) {
        applyValueStyle(
            key = key,
            value = valueTrimmed,
            lineOffset = lineOffset,
            line = line,
            colonIdx = indent + colonIdx,
        )
    }
    return key
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

        // Sub-keys of field definitions (`type`, `format`, `normalizers`, `operators`, `alias`).
        key in setOf("type", "format", "normalizers", "operators", "alias") ->
            SpanStyle(color = ColorKeyword)

        else -> SpanStyle(color = TextSecondary)
    }
}

/** Returns the [SpanStyle] for a YAML list item value based on the enclosing key. */
private fun resolveListItemStyle(parentKey: String?): SpanStyle {
    return when (parentKey) {
        "normalizers" -> SpanStyle(color = AccentOrange)
        "operators" -> SpanStyle(color = ColorOp)
        "argTypes" -> SpanStyle(color = ColorNumber)
        else -> SpanStyle(color = TextPrimary)
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
            "type" -> fieldTypeValueColor(value)
            "schema" -> SpanStyle(color = ColorString)
            else -> SpanStyle(color = ColorField)  // Field name values (including aliases)
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
        "argTypes" -> SpanStyle(color = ColorNumber)
        "normalizers" -> SpanStyle(color = AccentOrange)
        "operators" -> SpanStyle(color = ColorOp)
        else -> SpanStyle(color = TextPrimary)
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
                    end = bracketAbsStart + itemStart + item.length,
                )
                scanOffset = itemStart + item.length
            }
        }
    }
}

/** Returns a [SpanStyle] whose colour represents the YAML `type:` field value. */
private fun fieldTypeValueColor(value: String): SpanStyle {
    val color: Color = when (value.lowercase().trim()) {
        "text", "string" -> PrimaryBlueLight
        "integer", "int", "long" -> PrimaryBlue
        "decimal", "bigdecimal", "number" -> PrimaryBlue
        "boolean", "bool" -> AccentPurple
        "stringset", "string_set", "set" -> AccentGreen
        "date", "date_time", "datetime", "timestamp" -> AccentOrange
        else -> TextPrimary
    }
    return SpanStyle(color = color)
}
