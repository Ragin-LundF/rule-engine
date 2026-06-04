package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

/**
 * A syntax-highlighted YAML editor composable that supports:
 * - Syntax highlighting via [annotate]
 * - Code completion via [buildCompletions]
 * - YAML-aware indentation (Enter preserves indent; adds 2 extra spaces after `:`)
 * - Tab inserts 2 spaces; Shift+Tab removes 2 spaces
 *
 * The [value] / [onValueChange] pair follows the same contract as [BasicTextField].
 */
@Composable
fun YamlEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    editorType: YamlEditorType = YamlEditorType.FIELD_SCHEMA,
    annotate: (String) -> AnnotatedString = { AnnotatedString(it) },
    buildCompletions: ((YamlCursorContext) -> List<CompletionItem>)? = null,
    placeholder: String = "",
) {
    // ── Highlighted display value ─────────────────────────────────────────────
    val highlightedValue = remember(value.text) {
        TextFieldValue(
            annotatedString = annotate(value.text),
            selection = value.selection,
            composition = value.composition,
        )
    }

    // ── Autocomplete state ────────────────────────────────────────────────────
    var showAutoComplete      by remember { mutableStateOf(false) }
    var autoCompleteIndex     by remember { mutableStateOf(0) }
    var autoCompleteWord      by remember { mutableStateOf("") }
    var autoCompleteWordStart by remember { mutableStateOf(0) }
    var yamlContext           by remember { mutableStateOf(YamlCursorContext()) }
    var cursorRect            by remember { mutableStateOf(Rect.Zero) }
    var textLayoutResult      by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Re-compute word + context whenever cursor or text changes.
    LaunchedEffect(value.text, value.selection.start) {
        val cursor = value.selection.start
        val (wordStart, word) = extractCurrentWord(text = value.text, cursorPos = cursor)
        autoCompleteWordStart = wordStart
        autoCompleteWord = word
        autoCompleteIndex = 0

        val ctx = analyzeYamlContext(text = value.text, cursorPos = cursor)
        yamlContext = ctx

        // Show completions while a word prefix is being typed.
        showAutoComplete = word.isNotEmpty() && buildCompletions != null
    }

    val filteredSuggestions: List<CompletionItem> = remember(autoCompleteWord, yamlContext) {
        if (buildCompletions == null || autoCompleteWord.isEmpty()) {
            emptyList()
        } else {
            buildCompletions(yamlContext)
                .filter { it.label.startsWith(autoCompleteWord, ignoreCase = true) && it.label != autoCompleteWord }
                .sortedWith(compareBy({ it.kind.ordinal }, { it.label }))
                .take(8)
        }
    }

    // ── Accept a suggestion ───────────────────────────────────────────────────
    fun acceptSuggestion(item: CompletionItem) {
        val cursor = value.selection.start
        val newText = value.text.substring(0, autoCompleteWordStart) +
                item.insertText +
                value.text.substring(cursor)
        val newPos = autoCompleteWordStart + item.insertText.length
        onValueChange(TextFieldValue(text = newText, selection = TextRange(newPos)))
        showAutoComplete = false
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(modifier = modifier) {
        BasicTextField(
            value = highlightedValue,
            onValueChange = { newVal ->
                onValueChange(TextFieldValue(
                    text = newVal.text,
                    selection = newVal.selection,
                    composition = newVal.composition,
                ))
            },
            onTextLayout = { result ->
                textLayoutResult = result
                val cursor = value.selection.start.coerceIn(0, value.text.length.coerceAtLeast(0))
                runCatching { cursorRect = result.getCursorRect(cursor) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        // ── Autocomplete navigation ───────────────────────────
                        event.key == Key.Escape && showAutoComplete -> {
                            showAutoComplete = false
                            true
                        }
                        event.key == Key.DirectionDown && showAutoComplete -> {
                            autoCompleteIndex = (autoCompleteIndex + 1)
                                .coerceAtMost(filteredSuggestions.size - 1)
                            true
                        }
                        event.key == Key.DirectionUp && showAutoComplete -> {
                            autoCompleteIndex = (autoCompleteIndex - 1).coerceAtLeast(0)
                            true
                        }
                        event.key == Key.Tab && showAutoComplete && filteredSuggestions.isNotEmpty() -> {
                            acceptSuggestion(filteredSuggestions[autoCompleteIndex])
                            true
                        }

                        // ── Enter: YAML-aware indentation ─────────────────────
                        event.key == Key.Enter -> {
                            if (showAutoComplete) showAutoComplete = false
                            val text = value.text
                            val selStart = value.selection.start
                            val selEnd = value.selection.end
                            val lineStart = text.lastIndexOf('\n', selStart - 1) + 1
                            val currentLine = text.substring(lineStart, selStart)
                            val baseIndent = currentLine.takeWhile { it == ' ' || it == '\t' }
                            val trimmedLine = currentLine.trim()

                            // Add 2 extra spaces when the line ends with `:` (opens a YAML block).
                            val extraIndent = if (trimmedLine.endsWith(':')) "  " else ""

                            val newText = text.substring(0, selStart) + "\n" + baseIndent + extraIndent +
                                    text.substring(selEnd)
                            onValueChange(TextFieldValue(
                                text = newText,
                                selection = TextRange(selStart + 1 + baseIndent.length + extraIndent.length),
                            ))
                            true
                        }

                        // ── Tab: insert 2 spaces (YAML convention) ────────────
                        event.key == Key.Tab -> {
                            val text = value.text
                            val selStart = value.selection.start
                            val selEnd = value.selection.end
                            if (event.isShiftPressed) {
                                val lineStart = text.lastIndexOf('\n', selStart - 1) + 1
                                val spaces = text.substring(lineStart)
                                    .takeWhile { it == ' ' }.length.coerceAtMost(2)
                                if (spaces > 0) {
                                    val newText = text.substring(0, lineStart) +
                                            text.substring(lineStart + spaces)
                                    onValueChange(TextFieldValue(
                                        text = newText,
                                        selection = TextRange((selStart - spaces).coerceAtLeast(lineStart)),
                                    ))
                                }
                            } else {
                                val newText = text.substring(0, selStart) + "  " + text.substring(selEnd)
                                onValueChange(TextFieldValue(
                                    text = newText,
                                    selection = TextRange(selStart + 2),
                                ))
                            }
                            true
                        }

                        else -> false
                    }
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextPrimary,
                lineHeight = 17.sp,
            ),
            cursorBrush = SolidColor(PrimaryBlue),
        )

        // ── Placeholder when empty ────────────────────────────────────────────
        if (value.text.isEmpty() && placeholder.isNotEmpty()) {
            androidx.compose.material.Text(
                text = placeholder,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextMuted,
                ),
            )
        }

        // ── Autocomplete popup ────────────────────────────────────────────────
        if (showAutoComplete && filteredSuggestions.isNotEmpty()) {
            val density = LocalDensity.current
            val xPos = with(density) { cursorRect.left.toDp() }
            val yPos = with(density) { cursorRect.bottom.toDp() }
            AutoCompleteDropdown(
                modifier      = Modifier.offset(x = xPos, y = yPos),
                suggestions   = filteredSuggestions,
                selectedIndex = autoCompleteIndex,
                onSelect      = { acceptSuggestion(it) },
                onDismiss     = { showAutoComplete = false },
            )
        }
    }
}

