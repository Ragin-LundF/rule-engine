package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import ui.autocompletion.AutoCompleteDropdown
import ui.autocompletion.CompletionItem
import ui.autocompletion.extractCurrentWord

/**
 * A syntax-highlighted YAML editor composable that supports:
 * - Syntax highlighting via [annotate]
 * - Code completion via [buildCompletions]
 * - YAML-aware indentation (Enter preserves indent; adds 2 extra spaces after `:`)
 * - Tab inserts 2 spaces; Shift+Tab removes 2 spaces
 * - Completions shown when cursor is at the start of a line (not only while typing)
 *
 * The [value] / [onValueChange] pair follows the same contract as [BasicTextField].
 */
@Composable
fun YamlEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") editorType: YamlEditorType = YamlEditorType.FIELD_SCHEMA,
    annotate: (String) -> AnnotatedString = { AnnotatedString(it) },
    buildCompletions: ((YamlCursorContext) -> List<CompletionItem>)? = null,
    placeholder: String = "",
) {
    // ── Highlighted display value ─────────────────────────────────────────────
    // Cache the expensive AnnotatedString computation by text only.
    // The TextFieldValue is NOT cached by remember, so selection always reflects the
    // current cursor position (fixes arrow-key navigation: stale selection bug).
    val annotatedText = remember(value.text) { annotate(value.text) }
    val highlightedValue = TextFieldValue(
        annotatedString = annotatedText,
        selection = value.selection,
        composition = value.composition,
    )

    // ── Autocomplete state ────────────────────────────────────────────────────
    var showAutoComplete      by remember { mutableStateOf(false) }
    var autoCompleteIndex     by remember { mutableStateOf(0) }
    var autoCompleteWord      by remember { mutableStateOf("") }
    var autoCompleteWordStart by remember { mutableStateOf(0) }
    var yamlContext           by remember { mutableStateOf(YamlCursorContext()) }
    var cursorRect            by remember { mutableStateOf(Rect.Zero) }
    @Suppress("UNUSED_VARIABLE")
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

        // Show completions when:
        // - A word prefix is being typed, OR
        // - The cursor is at the start of a line (blank line content before cursor),
        //   which covers the empty-editor case and enables "show structure completions"
        //   without needing to type a prefix first.
        val lineStart = value.text.lastIndexOf('\n', cursor - 1) + 1
        val contentBeforeCursor = value.text.substring(lineStart, cursor)
        val isAtLineStart = contentBeforeCursor.isBlank()
        showAutoComplete = buildCompletions != null && (word.isNotEmpty() || isAtLineStart)
    }

    // Completions: show all context-relevant items when no prefix is typed,
    // or filter by prefix when one is being typed.
    val filteredSuggestions: List<CompletionItem> = remember(autoCompleteWord, yamlContext) {
        if (buildCompletions == null) {
            emptyList()
        } else if (autoCompleteWord.isEmpty()) {
            buildCompletions(yamlContext).take(8)
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
                        // Only consume direction keys when the popup is visible AND has items.
                        event.key == Key.Escape && showAutoComplete -> {
                            showAutoComplete = false
                            true
                        }
                        event.key == Key.DirectionDown && showAutoComplete && filteredSuggestions.isNotEmpty() -> {
                            autoCompleteIndex = (autoCompleteIndex + 1)
                                .coerceAtMost(filteredSuggestions.size - 1)
                            true
                        }
                        event.key == Key.DirectionUp && showAutoComplete && filteredSuggestions.isNotEmpty() -> {
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

