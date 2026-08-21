package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.core.analysis.VariableUsage
import ruleengine.dsl.parser.Parser
import ui.Bg
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.autocompletion.AutoCompleteDropdown
import ui.autocompletion.buildContextualCompletions
import ui.dsl.annotateRule
import ui.editor.AUTOCOMPLETE_HINT
import ui.editor.CodeEditing
import ui.editor.rules.RuleEditorState
import ui.editor.rules.autoClosingBraceDedent
import ui.editor.rules.drawTopLine
import ui.editor.rules.dslLineOpensBlock
import ui.editor.rules.isAbout
import ui.settings.SettingsController
import ui.theme.ThemeController
import ui.workbench.diagram.DiagramModeHost
import ui.workbench.diagram.diagramDataFor

/** The rule DSL indents four spaces per level. */
private const val DSL_INDENT = "    "

/** Main editor content: the code editor with line numbers and autocomplete, or the diagram view. */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun ColumnScope.MainEditorContentSection(
    state: RuleEditorState,
    diagramGraphicsLayer: GraphicsLayer,
    isDiagram: Boolean,
) {
    var ruleValue by state.ruleValue
    var parsedSchema by state.parsedSchema
    var parsedActionSchema by state.parsedActionSchema
    var diagnosticsList by state.diagnosticsList
    var showAutoComplete by state.showAutoComplete
    var autoCompleteIndex by state.autoCompleteIndex
    var autoCompleteWord by state.autoCompleteWord
    var dslContext by state.dslContext
    var cursorRect by state.cursorRect

    // ── Local editor state ─────────────────────────────────────────────────────
    val editorScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(value = null) }

    // ── Parsed rules for live diagram view ────────────────────────────────────
    val diagramView by state.diagramView
    val diagramRules = remember(ruleValue.text) {
        runCatching { Parser(input = ruleValue.text).parseRules() }.getOrElse { emptyList() }
    }

    // ── Syntax-highlighted display value ──────────────────────────────────────
    // The final TextFieldValue is NOT wrapped in remember so its selection always reflects the
    // current cursor position — this fixes arrow-key navigation (stale-selection bug).
    //
    // `isDark` is a key, not incidental: the colours annotateRule bakes in come from the palette,
    // and a snapshot read inside a remember block does not invalidate that block. Without the key
    // the spans keep the palette that was active when the text last changed, so switching theme
    // left dark-mode token colours on a light background.
    // Only what belongs to the file on screen. An entry-wide validation reports every file, and each
    // diagnostic's line is relative to its own — so underlining them all would mark lines of this file
    // for problems in another.
    val openFile = state.selectedManifestRuleFile.value
    val underlined = remember(diagnosticsList, openFile) {
        diagnosticsList.filter { diagnostic -> diagnostic.isAbout(openFile = openFile) }
    }
    val annotatedRule = remember(
        ruleValue.text,
        parsedSchema,
        parsedActionSchema,
        underlined,
        ThemeController.isDark,
    ) {
        annotateRule(
            text = ruleValue.text,
            schema = parsedSchema,
            actions = parsedActionSchema,
            diagnostics = underlined
        )
    }
    val highlightedValue = TextFieldValue(
        annotatedString = annotatedRule,
        selection = ruleValue.selection,
        composition = ruleValue.composition,
    )

    // Variables the open buffer publishes, with the clause that writes each one. Derived from the text
    // rather than from the saved entry so a `set` clause is offered as soon as it is typed, before the
    // file is written to disk. The kind is what lets an action declaring `variable_list` be offered only
    // the accumulators.
    val variableKinds = remember(ruleValue.text) {
        runCatching {
            Parser(input = ruleValue.text).parseRules()
                .flatMap { rule -> VariableUsage.writeKindsOf(rule = rule).entries }
                .associate { entry -> entry.key to entry.value }
        }.getOrDefault(defaultValue = emptyMap())
    }
    val variableNames = remember(variableKinds) { variableKinds.keys.toList() }

    // ── Context-aware autocomplete suggestions ────────────────────────────────
    val filteredSuggestions = remember(autoCompleteWord, dslContext, parsedSchema, parsedActionSchema, variableKinds) {
        val candidates = buildContextualCompletions(
            context = dslContext,
            schema = parsedSchema,
            actionSchema = parsedActionSchema,
            variableNames = variableNames,
            variableKinds = variableKinds,
        )
        CodeEditing.filterSuggestions(
            candidates = candidates,
            word = autoCompleteWord,
            label = { item -> item.label },
            kindOrder = { item -> item.kind.ordinal },
        )
    }

    // ── Code Editor or Diagram view ───────────────────────────────
    if (isDiagram) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape = RoundedCornerShape(size = 6.dp))
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp)),
        ) {
            // Pass the capture layer down so recording happens on the
            // full-height content column, not on this clipped viewport box.
            DiagramModeHost(
                view = diagramView,
                data = diagramDataFor(state = state, rules = diagramRules),
                captureLayer = diagramGraphicsLayer,
            )
        }
    } else {

        val lineNumberWidthDp = 48.dp
        val editorPaddingDp = 14.dp
        val lineCount = remember(ruleValue.text) {
            ruleValue.text.lines().size.coerceAtLeast(minimumValue = 1)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape = RoundedCornerShape(size = 6.dp))
                .background(color = Bg)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Line-number gutter ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .width(lineNumberWidthDp)
                        .fillMaxHeight()
                        .background(color = ui.BgSurface)
                        .drawTopLine(w = 0.dp, color = BorderColor),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(state = editorScrollState, enabled = false)
                            .padding(top = editorPaddingDp, end = 8.dp, start = 4.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        val currentLine = remember(
                            key1 = ruleValue.text,
                            key2 = ruleValue.selection.start
                        ) {
                            ruleValue.text.take(
                                n = ruleValue.selection.start.coerceIn(0, ruleValue.text.length)
                            ).count { it == '\n' } + 1
                        }
                        repeat(times = lineCount) { i ->
                            Text(
                                text = "${i + 1}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = if (i + 1 == currentLine) {
                                        PrimaryBlue.copy(alpha = 0.7f)
                                    } else TextMuted,
                                ),
                            )
                        }
                    }
                }
                // Gutter separator
                Box(Modifier.width(1.dp).fillMaxHeight().background(BorderColor))

                // ── Scrollable text area ───────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(editorScrollState),
                ) {
                    BasicTextField(
                        value = highlightedValue,
                        onValueChange = { newVal ->
                            // A space-based shortcut can also arrive as text input, which the key
                            // handler never sees; drop that one space rather than let it through.
                            val stray = state.swallowShortcutSpace.value && CodeEditing.isStraySpaceInsertion(
                                current = ruleValue.text,
                                caret = ruleValue.selection.start,
                                candidate = newVal.text,
                            )
                            state.swallowShortcutSpace.value = false
                            if (stray) return@BasicTextField

                            val isNewChar = newVal.text.length == ruleValue.text.length + 1
                            val cursorPos = newVal.selection.start
                            // Auto-dedent `}` when typed on an otherwise-whitespace line.
                            if (isNewChar && cursorPos > 0 &&
                                newVal.text.getOrNull(index = cursorPos - 1) == '}'
                            ) {
                                val (dedentedText, removed) = autoClosingBraceDedent(
                                    text = newVal.text, bracePos = cursorPos - 1,
                                )
                                ruleValue = TextFieldValue(
                                    text = dedentedText,
                                    selection = TextRange(
                                        index = (cursorPos - removed).coerceAtLeast(
                                            minimumValue = 0
                                        )
                                    ),
                                    composition = newVal.composition,
                                )
                            } else {
                                ruleValue =
                                    TextFieldValue(newVal.text, newVal.selection, newVal.composition)
                            }
                        },
                        onTextLayout = { result ->
                            textLayoutResult = result
                            val cursor = ruleValue.selection.start
                                .coerceIn(0, ruleValue.text.length.coerceAtLeast(minimumValue = 0))
                            runCatching { cursorRect = result.getCursorRect(offset = cursor) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 200.dp)
                            .padding(all = editorPaddingDp)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    // ── Open the completion popup on demand ───────────
                                    SettingsController.autoCompleteShortcut.matches(event = event) -> {
                                        state.autoCompleteAnchor.value = state.autoCompleteWordStart.value
                                        state.swallowShortcutSpace.value =
                                            SettingsController.autoCompleteShortcut.insertsCharacter
                                        showAutoComplete = true
                                        true
                                    }

                                    // ── Autocomplete navigation ───────────────────────
                                    // Only consume direction keys when suggestions are available.
                                    event.key == Key.Escape && showAutoComplete -> {
                                        showAutoComplete = false; true
                                    }

                                    event.key == Key.DirectionDown && showAutoComplete &&
                                            filteredSuggestions.isNotEmpty() -> {
                                        autoCompleteIndex = (autoCompleteIndex + 1)
                                            .coerceAtMost(maximumValue = filteredSuggestions.size - 1)
                                        true
                                    }

                                    event.key == Key.DirectionUp && showAutoComplete &&
                                            filteredSuggestions.isNotEmpty() -> {
                                        autoCompleteIndex = (autoCompleteIndex - 1).coerceAtLeast(
                                            minimumValue = 0
                                        )
                                        true
                                    }

                                    event.key == Key.Tab && showAutoComplete &&
                                            filteredSuggestions.isNotEmpty() -> {
                                        state.acceptSuggestion(item = filteredSuggestions[autoCompleteIndex])
                                        true
                                    }

                                    // ── Enter: smart DSL indentation ──────────────────────
                                    event.key == Key.Enter -> {
                                        if (showAutoComplete) showAutoComplete = false
                                        val edit = CodeEditing.breakLine(
                                            text = ruleValue.text,
                                            selectionStart = ruleValue.selection.start,
                                            selectionEnd = ruleValue.selection.end,
                                            indentUnit = DSL_INDENT,
                                            opensBlock = { line -> dslLineOpensBlock(trimmedLine = line) },
                                        )
                                        ruleValue = TextFieldValue(
                                            edit.text,
                                            selection = TextRange(index = edit.cursor),
                                        )
                                        true
                                    }
                                    // ── Tab: indent / dedent ──────────────────────────
                                    event.key == Key.Tab -> {
                                        val edit = if (event.isShiftPressed) {
                                            CodeEditing.dedent(
                                                text = ruleValue.text,
                                                selectionStart = ruleValue.selection.start,
                                                indentUnit = DSL_INDENT,
                                            )
                                        } else {
                                            CodeEditing.indent(
                                                text = ruleValue.text,
                                                selectionStart = ruleValue.selection.start,
                                                selectionEnd = ruleValue.selection.end,
                                                indentUnit = DSL_INDENT,
                                            )
                                        }
                                        edit?.let {
                                            ruleValue = TextFieldValue(it.text, TextRange(index = it.cursor))
                                        }
                                        true
                                    }

                                    else -> false
                                }
                            },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(value = PrimaryBlue),
                    )
                }
            }

            // ── Placeholder (when editor is empty) ─────────────────────
            if (ruleValue.text.isEmpty()) {
                Text(
                    text = "# Write your rules here…\nrule \"example\" {\n" +
                            "    when field > value\n    then action \"result\"\n}",
                    modifier = Modifier.padding(
                        start = lineNumberWidthDp + 1.dp + editorPaddingDp,
                        top = editorPaddingDp,
                    ),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextMuted,
                        lineHeight = 20.sp,
                    ),
                )
            }

            // ── Autocomplete popup overlay ─────────────────────────────
            if (showAutoComplete && filteredSuggestions.isNotEmpty()) {
                val density = LocalDensity.current
                val xPos = with(receiver = density) {
                    lineNumberWidthDp + 1.dp + editorPaddingDp + cursorRect.left.toDp()
                }
                val yPos = with(receiver = density) {
                    editorPaddingDp + (cursorRect.bottom - editorScrollState.value).toDp()
                }
                AutoCompleteDropdown(
                    modifier = Modifier.offset(x = xPos, y = yPos),
                    suggestions = filteredSuggestions,
                    selectedIndex = autoCompleteIndex,
                    onSelect = { state.acceptSuggestion(item = it) },
                )
            }
        }

        Text(
            text = AUTOCOMPLETE_HINT,
            style = MaterialTheme.typography.caption,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
    } // end CODE view
}

