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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
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
import ui.theme.ThemeController
import ui.util.Words

/** YAML indents two spaces per level. */
private const val YAML_INDENT = "  "

/**
 * Everything the completion popup needs to know about itself.
 *
 * A holder rather than eight `var`s in the composable so the key bindings can be plain functions:
 * they were the bulk of this file, and inline they could only be read by scrolling past the layout.
 */
private class YamlAutoCompleteState {
    var visible by mutableStateOf(value = false)
    var index by mutableStateOf(value = 0)
    var word by mutableStateOf(value = "")
    var wordStart by mutableStateOf(value = 0)
    var context by mutableStateOf(value = YamlCursorContext())
    var cursorRect by mutableStateOf(value = Rect.Zero)

    /** The offset the popup was opened for; it closes when the caret leaves that word. */
    var anchor by mutableStateOf(value = -1)

    /** Set when the shortcut that opened the popup also types a character. */
    var swallowShortcutSpace by mutableStateOf(value = false)
}

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
    // The TextFieldValue is NOT cached by remember, so selection always reflects the current
    // cursor position (fixes arrow-key navigation: stale selection bug).
    //
    // `isDark` is a key for the same reason as in the rule editor: the annotator bakes palette
    // colours into the spans, and a snapshot read inside a remember block does not invalidate it.
    val annotatedText = remember(value.text, ThemeController.isDark) { annotate(value.text) }
    val highlightedValue = TextFieldValue(
        annotatedString = annotatedText,
        selection = value.selection,
        composition = value.composition,
    )

    val ac = remember { YamlAutoCompleteState() }

    TrackYamlCursor(value = value, ac = ac)

    // Completions: show all context-relevant items when no prefix is typed,
    // or filter by prefix when one is being typed.
    val filteredSuggestions: List<CompletionItem> = remember(ac.word, ac.context) {
        if (buildCompletions == null) {
            emptyList()
        } else {
            CodeEditing.filterSuggestions(
                candidates = buildCompletions(ac.context),
                word = ac.word,
                label = { item -> item.label },
                kindOrder = { item -> item.kind.ordinal },
            )
        }
    }

    fun acceptSuggestion(item: CompletionItem) {
        val edit = CodeEditing.applySuggestion(
            text = value.text,
            wordStart = ac.wordStart,
            cursor = value.selection.start,
            insertText = item.insertText,
        )
        onValueChange(TextFieldValue(text = edit.text, selection = TextRange(edit.cursor)))
        ac.visible = false
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
        YamlTextField(
            highlightedValue = highlightedValue,
            value = value,
            onValueChange = onValueChange,
            ac = ac,
            suggestions = filteredSuggestions,
            completionsAvailable = buildCompletions != null,
            accept = ::acceptSuggestion,
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
        if (ac.visible && filteredSuggestions.isNotEmpty()) {
            val density = LocalDensity.current
            val xPos = with(density) { ac.cursorRect.left.toDp() }
            val yPos = with(density) { ac.cursorRect.bottom.toDp() }
            AutoCompleteDropdown(
                modifier      = Modifier.offset(x = xPos, y = yPos),
                suggestions   = filteredSuggestions,
                selectedIndex = ac.index,
                onSelect      = { acceptSuggestion(it) },
            )
        }
    }
}

/**
 * The text field itself: highlighted display value in, plain edits out.
 *
 * Reports the caret's on-screen rectangle through [ac] so the completion popup can sit under it.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun YamlTextField(
    highlightedValue: TextFieldValue,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    ac: YamlAutoCompleteState,
    suggestions: List<CompletionItem>,
    completionsAvailable: Boolean,
    accept: (CompletionItem) -> Unit,
) {
    BasicTextField(
        value = highlightedValue,
        onValueChange = { newVal ->
            // A space-based shortcut can also arrive through the text-input path, which the key
            // handler never sees; drop that one space instead of letting it into the document.
            val stray = ac.swallowShortcutSpace && CodeEditing.isStraySpaceInsertion(
                current = value.text,
                caret = value.selection.start,
                candidate = newVal.text,
            )
            ac.swallowShortcutSpace = false
            if (!stray) {
                onValueChange(
                    TextFieldValue(
                        text = newVal.text,
                        selection = newVal.selection,
                        composition = newVal.composition,
                    ),
                )
            }
        },
        onTextLayout = { result ->
            val cursor = value.selection.start.coerceIn(0, value.text.length.coerceAtLeast(0))
            runCatching { ac.cursorRect = result.getCursorRect(cursor) }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                handleYamlKey(
                    event = event,
                    ac = ac,
                    value = value,
                    onValueChange = onValueChange,
                    suggestions = suggestions,
                    completionsAvailable = completionsAvailable,
                    accept = accept,
                )
            },
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextPrimary,
            lineHeight = 17.sp,
        ),
        cursorBrush = SolidColor(PrimaryBlue),
    )
}

/** Recomputes the word under the caret and the YAML context whenever either can have changed. */
@Suppress("FunctionNaming")
@Composable
private fun TrackYamlCursor(value: TextFieldValue, ac: YamlAutoCompleteState) {
    LaunchedEffect(value.text, value.selection.start) {
        val cursor = value.selection.start
        val (wordStart, word) = Words.currentWord(text = value.text, cursorPos = cursor)
        ac.wordStart = wordStart
        ac.word = word
        ac.index = 0
        ac.context = analyzeYamlContext(text = value.text, cursorPos = cursor)

        // The popup is never offered on its own. Once open it stays anchored to the word it was
        // opened for, so typing narrows it; it closes only when the caret leaves that word.
        if (ac.visible && !CodeEditing.isAnchorLive(
                text = value.text,
                cursor = cursor,
                anchor = ac.anchor,
            )
        ) {
            ac.visible = false
        }
    }
}

/**
 * The editor's key bindings, in the order they must be tried.
 *
 * Popup keys come first because Tab means two different things: accept the highlighted suggestion
 * while the popup is up, indent otherwise.
 */
@Suppress("LongParameterList")
private fun handleYamlKey(
    event: KeyEvent,
    ac: YamlAutoCompleteState,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestions: List<CompletionItem>,
    completionsAvailable: Boolean,
    accept: (CompletionItem) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return handlePopupKey(
        event = event,
        ac = ac,
        value = value,
        suggestions = suggestions,
        completionsAvailable = completionsAvailable,
        accept = accept,
    ) || handleIndentKey(event = event, ac = ac, value = value, onValueChange = onValueChange)
}

/**
 * Opening, navigating and accepting a completion.
 *
 * The direction keys are only consumed when the popup is visible *and* has items, so an empty popup
 * never swallows a caret move.
 */
@Suppress("LongParameterList", "ReturnCount")
private fun handlePopupKey(
    event: KeyEvent,
    ac: YamlAutoCompleteState,
    value: TextFieldValue,
    suggestions: List<CompletionItem>,
    completionsAvailable: Boolean,
    accept: (CompletionItem) -> Unit,
): Boolean {
    if (SettingsController.autoCompleteShortcut.matches(event = event)) {
        val (wordStart, _) = Words.currentWord(text = value.text, cursorPos = value.selection.start)
        ac.anchor = wordStart
        ac.swallowShortcutSpace = SettingsController.autoCompleteShortcut.insertsCharacter
        ac.visible = completionsAvailable
        return true
    }
    if (!ac.visible) return false
    if (event.key == Key.Escape) {
        ac.visible = false
        return true
    }
    if (suggestions.isEmpty()) return false
    return when (event.key) {
        Key.DirectionDown -> {
            ac.index = (ac.index + 1).coerceAtMost(suggestions.size - 1)
            true
        }

        Key.DirectionUp -> {
            ac.index = (ac.index - 1).coerceAtLeast(0)
            true
        }

        Key.Tab -> {
            accept(suggestions[ac.index])
            true
        }

        else -> false
    }
}

/** Enter keeps the YAML indent (and adds one level after a `:`); Tab and Shift+Tab shift it. */
private fun handleIndentKey(
    event: KeyEvent,
    ac: YamlAutoCompleteState,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
): Boolean = when (event.key) {
    Key.Enter -> {
        if (ac.visible) ac.visible = false
        val edit = CodeEditing.breakLine(
            text = value.text,
            selectionStart = value.selection.start,
            selectionEnd = value.selection.end,
            indentUnit = YAML_INDENT,
            // A line ending in `:` opens a YAML block.
            opensBlock = { line -> line.endsWith(char = ':') },
        )
        onValueChange(TextFieldValue(text = edit.text, selection = TextRange(edit.cursor)))
        true
    }

    Key.Tab -> {
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
        edit?.let { onValueChange(TextFieldValue(text = it.text, selection = TextRange(it.cursor))) }
        true
    }

    else -> false
}
