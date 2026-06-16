package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import ruleengine.compiler.Validator
import ruleengine.dsl.parser.Parser
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.editor.rules.isContextuallyImmediate
import ui.editor.rules.sections.DiagnosticsSection
import ui.editor.rules.sections.LeftPanelSection
import ui.editor.rules.sections.RightPanelSection
import ui.editor.rules.sections.StatusBarSection
import ui.editor.rules.sections.TopBarSection

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
actual fun RuleEditor() {
    val scope = rememberCoroutineScope()
    // Centralized state container for the editor
    val state = remember { RuleEditorState(scope = scope) }

    // ── Auto-load first manifest entry when manifest is newly set ─────────────
    LaunchedEffect(key1 = state.parsedManifest.value) {
        val manifest = state.parsedManifest.value ?: run {
            state.selectedManifestEntry.value = null
            return@LaunchedEffect
        }
        val first = manifest.entries.firstOrNull() ?: return@LaunchedEffect
        // Only auto-load when no entry is already selected (prevents unwanted override
        // when the same manifest is re-parsed after a text edit).
        if (state.selectedManifestEntry.value == null) {
            state.loadManifestEntry(entry = first)
        }
    }

    // ── Track word + DSL context on every cursor move ─────────────────────────
    LaunchedEffect(key1 = state.ruleValue.value.text, key2 = state.ruleValue.value.selection.start) {
        val cursor = state.ruleValue.value.selection.start
        val (wordStart, word) = extractCurrentWord(text = state.ruleValue.value.text, cursorPos = cursor)
        state.autoCompleteWordStart.value = wordStart
        state.autoCompleteWord.value = word
        state.autoCompleteIndex.value = 0

        val ctx = analyzeDslContext(
            text = state.ruleValue.value.text,
            cursorPos = cursor,
            schema = state.parsedSchema.value
        )
        state.dslContext.value = ctx

        val lastChar = if (cursor > 0) state.ruleValue.value.text.getOrNull(index = cursor - 1) else null
        val afterSpace = lastChar == ' ' || lastChar == '\n'
        state.showAutoComplete.value = state.autoCompleteWord.value.isNotEmpty() ||
                (afterSpace && isContextuallyImmediate(context = ctx))
    }

    // ── Debounced auto-validation ──────────────────────────────────────────────
    LaunchedEffect(key1 = state.ruleValue.value.text) {
        if (state.ruleValue.value.text.isBlank()) {
            state.diagnosticsList.value = emptyList()
            state.diagnosticsText.value = ""
            return@LaunchedEffect
        }
        delay(timeMillis = 700)
        runCatching {
            if (state.parsedSchema.value == null) return@LaunchedEffect
            val asts = Parser(input = state.ruleValue.value.text).parseRules()
            val result = Validator.validate(
                asts = asts,
                schema = state.parsedSchema.value!!,
                actions = state.parsedActionSchema.value
            )
            state.diagnosticsList.value = result.diagnostics
            state.diagnosticsText.value = if (result.isValid) "No issues found" else ""
            state.setStatus(
                msg = if (result.isValid) "✓ Validation passed" else "✗ ${result.diagnostics.size} issue(s)",
                kind = if (result.isValid) StatusKind.SUCCESS else StatusKind.ERROR,
            )
        }
    }

    // ── Parsed rules for the expanded diagram window ───────────────────────────
    val diagramRulesForWindow = remember(key1 = state.ruleValue.value.text) {
        runCatching { Parser(input = state.ruleValue.value.text).parseRules() }.getOrElse { emptyList() }
    }

    Column(modifier = Modifier.fillMaxSize().background(color = Bg)) {

        // ── Top Bar ───────────────────────────────────────────────────────────
        TopBarSection(state = state, scope = scope)

        // ── Main layout ───────────────────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier.weight(weight = 1f).fillMaxWidth().padding(all = 12.dp),
        ) {
            val leftWidthDp = maxWidth * state.splitFraction.value

            Row(modifier = Modifier.fillMaxSize()) {

                // ── Left panel ────────────────────────────────────────────────
                LeftPanelSection(
                    state = state,
                    scope = scope,
                    modifier = Modifier.width(width = leftWidthDp).fillMaxHeight(),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // ── Right panel ───────────────────────────────────────────────
                RightPanelSection(
                    state = state,
                    scope = scope,
                    modifier = Modifier.weight(weight = 0.67f).fillMaxHeight(),
                )
            }
        }

        // ── Diagnostics ───────────────────────────────────────────────────────
        DiagnosticsSection(state = state)

        // ── Status Bar ────────────────────────────────────────────────────────
        StatusBarSection(state = state)
    }

    // ── Expanded diagram window ───────────────────────────────────────────────
    // Opened via the "⤢ Expand" button in diagram mode.
    // Shares the same diagramRules state so it updates live while editing.
    if (state.showExpandedDiagram.value) {
        Window(
            onCloseRequest = { state.showExpandedDiagram.value = false },
            title = "Rule Diagram — Full View",
            state = rememberWindowState(size = DpSize(width = 1400.dp, height = 900.dp)),
        ) {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Bg,
                ) {
                    RuleDiagramView(rules = diagramRulesForWindow)
                }
            }
        }
    }
}

