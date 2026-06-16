package ui

import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldSchema
import ui.autocompletion.AutoCompleteDropdown as AutoCompleteDropdownImpl
import ui.autocompletion.CompletionItem as CompletionItemImpl
import ui.autocompletion.CompletionKind as CompletionKindImpl
import ui.autocompletion.buildAllCompletions as buildAllCompletionsImpl
import ui.autocompletion.buildContextualCompletions as buildContextualCompletionsImpl
import ui.autocompletion.extractCurrentWord as extractCurrentWordImpl

// Type aliases to keep existing callers compiling against `ui` package types.
typealias CompletionKind = CompletionKindImpl
typealias CompletionItem = CompletionItemImpl

/** Forwarding functions — keep the public API at `ui` while implementation lives in `ui.autocompletion`. */
fun buildContextualCompletions(
    context: DslCursorContext,
    schema: FieldSchema?,
    actionSchema: ActionSchema?
): List<CompletionItem> {
    return buildContextualCompletionsImpl(context = context, schema = schema, actionSchema = actionSchema)
}

fun buildAllCompletions(schema: FieldSchema?, actionSchema: ActionSchema?): List<CompletionItem> {
    return buildAllCompletionsImpl(schema = schema, actionSchema = actionSchema)
}

fun extractCurrentWord(text: String, cursorPos: Int): Pair<Int, String> {
    return extractCurrentWordImpl(text = text, cursorPos = cursorPos)
}

@androidx.compose.runtime.Composable
fun AutoCompleteDropdown(
    suggestions: List<CompletionItem>,
    selectedIndex: Int,
    onSelect: (CompletionItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    // Map types back to implementation types (typealias keeps them compatible)
    AutoCompleteDropdownImpl(
        suggestions = suggestions.map { it },
        selectedIndex = selectedIndex,
        onSelect = { onSelect(it) },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
