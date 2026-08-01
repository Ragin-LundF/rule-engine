package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.autocompletion.AutoCompleteDropdown
import ui.autocompletion.CompletionItem
import ui.editor.CodeEditing
import ui.settings.SettingsController
import ui.util.Words

/** YAML indents two spaces per level. */
private const val YAML_INDENT = "  "

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
    var autoCompleteAnchor    by remember { mutableStateOf(-1) }
    var swallowShortcutSpace  by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE")
    var textLayoutResult      by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Re-compute word + context whenever cursor or text changes.
    LaunchedEffect(value.text, value.selection.start) {
        val cursor = value.selection.start
        val (wordStart, word) = Words.currentWord(text = value.text, cursorPos = cursor)
        autoCompleteWordStart = wordStart
        autoCompleteWord = word
        autoCompleteIndex = 0

        val ctx = analyzeYamlContext(text = value.text, cursorPos = cursor)
        yamlContext = ctx

        // The popup is never offered on its own. Once open it stays anchored to the word it was
        // opened for, so typing narrows it; it closes only when the caret leaves that word.
        if (showAutoComplete && !CodeEditing.isAnchorLive(
                text = value.text,
                cursor = cursor,
                anchor = autoCompleteAnchor,
            )
        ) {
            showAutoComplete = false
        }
    }

    // Completions: show all context-relevant items when no prefix is typed,
    // or filter by prefix when one is being typed.
    val filteredSuggestions: List<CompletionItem> = remember(autoCompleteWord, yamlContext) {
        if (buildCompletions == null) {
            emptyList()
        } else {
            CodeEditing.filterSuggestions(
                candidates = buildCompletions(yamlContext),
                word = autoCompleteWord,
                label = { item -> item.label },
                kindOrder = { item -> item.kind.ordinal },
            )
        }
    }

    // ── Accept a suggestion ───────────────────────────────────────────────────
    fun acceptSuggestion(item: CompletionItem) {
        val edit = CodeEditing.applySuggestion(
            text = value.text,
            wordStart = autoCompleteWordStart,
            cursor = value.selection.start,
            insertText = item.insertText,
        )
        onValueChange(TextFieldValue(text = edit.text, selection = TextRange(edit.cursor)))
        showAutoComplete = false
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    // Framed like the rule editor: without a border and an input background the field reads as
    // static text, and there is nothing to tell you it can be typed into.
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = Bg)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .padding(all = 10.dp),
    ) {
        BasicTextField(
            value = highlightedValue,
            onValueChange = { newVal ->
                // A space-based shortcut can also arrive through the text-input path, which the key
                // handler never sees; drop that one space instead of letting it into the document.
                val stray = swallowShortcutSpace && CodeEditing.isStraySpaceInsertion(
                    current = value.text,
                    caret = value.selection.start,
                    candidate = newVal.text,
                )
                swallowShortcutSpace = false
                if (!stray) {
                    onValueChange(TextFieldValue(
                        text = newVal.text,
                        selection = newVal.selection,
                        composition = newVal.composition,
                    ))
                }
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
                        // ── Open the completion popup on demand ───────────────
                        SettingsController.autoCompleteShortcut.matches(event = event) -> {
                            val (wordStart, _) = Words.currentWord(
                                text = value.text,
                                cursorPos = value.selection.start,
                            )
                            autoCompleteAnchor = wordStart
                            swallowShortcutSpace = SettingsController.autoCompleteShortcut.insertsCharacter
                            showAutoComplete = buildCompletions != null
                            true
                        }

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
                            val edit = CodeEditing.breakLine(
                                text = value.text,
                                selectionStart = value.selection.start,
                                selectionEnd = value.selection.end,
                                indentUnit = YAML_INDENT,
                                // A line ending in `:` opens a YAML block.
                                opensBlock = { line -> line.endsWith(char = ':') },
                            )
                            onValueChange(
                                TextFieldValue(text = edit.text, selection = TextRange(edit.cursor))
                            )
                            true
                        }

                        // ── Tab: insert 2 spaces (YAML convention) ────────────
                        event.key == Key.Tab -> {
                            val edit = if (event.isShiftPressed) {
                                CodeEditing.dedent(
                                    text = value.text,
                                    selectionStart = value.selection.start,
                                    indentUnit = YAML_INDENT,
                                )
                            } else {
                                CodeEditing.indent(
                                    text = value.text,
                                    selectionStart = value.selection.start,
                                    selectionEnd = value.selection.end,
                                    indentUnit = YAML_INDENT,
                                )
                            }
                            edit?.let {
                                onValueChange(TextFieldValue(text = it.text, selection = TextRange(it.cursor)))
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
            )
        }
    }
}

