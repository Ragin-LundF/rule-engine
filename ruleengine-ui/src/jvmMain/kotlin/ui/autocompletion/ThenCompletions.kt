package ui.autocompletion

import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionSchema
import ui.autocompletion.model.CompletionItem
import ui.autocompletion.model.CompletionKind

/** Completions offered inside a rule's `then` block. */
internal fun buildActionNameCompletions(actionSchema: ActionSchema?): List<CompletionItem> {
    val schema = actionSchema ?: return emptyList()
    return schema.actions.map { (name, def) ->
        val argPlaceholders = def.argTypes.joinToString(separator = " ") { argType ->
            when (argType) {
                ActionArgType.INTEGER -> "0"
                ActionArgType.DECIMAL -> "0.0"
                ActionArgType.STRING -> "\"value\""
            }
        }
        val insertText = if (argPlaceholders.isNotEmpty()) "$name $argPlaceholders" else name
        CompletionItem(
            label = name,
            insertText = insertText,
            kind = CompletionKind.ACTION,
            hint = def.argTypes.joinToString(separator = ", ") { it.name.lowercase() }
        )
    }
}

internal fun buildActionArgCompletions(
    actionName: String,
    actionSchema: ActionSchema?
): List<CompletionItem> {
    val def = actionSchema?.actions?.get(actionName) ?: return emptyList()
    return def.argTypes.mapIndexed { idx, argType ->
        val placeholder = when (argType) {
            ActionArgType.INTEGER -> "0"
            ActionArgType.DECIMAL -> "0.0"
            ActionArgType.STRING -> "\"value\""
        }
        CompletionItem(
            label = placeholder,
            insertText = placeholder,
            kind = CompletionKind.LITERAL,
            hint = "arg${idx + 1}: ${argType.name.lowercase()}"
        )
    }
}
