package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import ui.dsl.analyzeDslContext
import ui.editor.CodeEditing
import ui.editor.rules.RuleEditorState
import ui.editor.rules.RuleValidationRunner
import ui.editor.rules.inheritedVariablesForOpenBuffer
import ui.editor.rules.model.RuleValidationOutcome
import ui.editor.rules.model.StatusKind
import ui.util.Words

private const val VALIDATION_DEBOUNCE_MS = 700L

/**
 * The two effects that react to the rule text: autocomplete context tracking, and validation.
 *
 * A composable rather than two calls in the screen so the effects keep a single, obvious home. They
 * are declared at the same point in the composition as before, which is what preserves their order
 * relative to the builder's selection effects further down.
 */
@Suppress("FunctionNaming")
@Composable
fun RuleEditorTextEffects(state: RuleEditorState) {
    TrackCursorContext(state = state)
    DebouncedValidation(state = state)
}

/** Recomputes the word under the caret and the DSL context on every cursor move. */
@Suppress("FunctionNaming")
@Composable
private fun TrackCursorContext(state: RuleEditorState) {
    LaunchedEffect(key1 = state.ruleValue.value.text, key2 = state.ruleValue.value.selection.start) {
        val cursor = state.ruleValue.value.selection.start
        val (wordStart, word) = Words.currentWord(text = state.ruleValue.value.text, cursorPos = cursor)
        state.autoCompleteWordStart.value = wordStart
        state.autoCompleteWord.value = word
        state.autoCompleteIndex.value = 0

        val ctx = analyzeDslContext(text = state.ruleValue.value.text, cursorPos = cursor)
        state.dslContext.value = ctx

        // Never offered on its own. Once open it stays anchored to the word it was opened for, so
        // typing narrows it; it closes only when the caret leaves that word.
        if (state.showAutoComplete.value && !CodeEditing.isAnchorLive(
                text = state.ruleValue.value.text,
                cursor = cursor,
                anchor = state.autoCompleteAnchor.value,
            )
        ) {
            state.showAutoComplete.value = false
        }
    }
}

/**
 * Validates the rule text after a pause in typing.
 *
 * Silently drops a parse failure — unlike the Validate button, which reports it. This pass runs
 * while the author is mid-keystroke, where half-written text failing to parse is the normal state.
 */
@Suppress("FunctionNaming")
@Composable
private fun DebouncedValidation(state: RuleEditorState) {
    LaunchedEffect(key1 = state.ruleValue.value.text) {
        if (state.ruleValue.value.text.isBlank()) {
            state.diagnosticsList.value = emptyList()
            state.diagnosticsText.value = ""
            return@LaunchedEffect
        }
        delay(timeMillis = VALIDATION_DEBOUNCE_MS)
        // The guard stays a return from the effect, not from a lambda: with no schema there is
        // nothing to validate against and the previous diagnostics must be left as they are.
        val schema = state.ruleSchema ?: return@LaunchedEffect

        when (
            val outcome = RuleValidationRunner.run(
                ruleText = state.ruleValue.value.text,
                schema = schema,
                actions = state.parsedActionSchema.value,
                inheritedVariables = state.inheritedVariablesForOpenBuffer(),
            )
        ) {
            is RuleValidationOutcome.Completed -> {
                state.diagnosticsList.value = outcome.diagnostics
                state.diagnosticsText.value = if (outcome.isValid) "No issues found" else ""
                state.setStatus(
                    msg = if (outcome.isValid) "✓ Validation passed" else "✗ ${outcome.diagnostics.size} issue(s)",
                    kind = if (outcome.isValid) StatusKind.SUCCESS else StatusKind.ERROR,
                )
            }

            is RuleValidationOutcome.Threw -> Unit
        }
    }
}
