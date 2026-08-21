package ui.autocompletion

import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionSchema
import ui.autocompletion.model.CompletionItem
import ui.autocompletion.model.CompletionKind

/**
 * The placeholder an argument of [argType] is inserted as.
 *
 * A `$` for a declared variable argument, which is not a value to type over but a reference to pick —
 * `buildActionArgCompletions` follows it with the variables that actually fit.
 */
private fun placeholderFor(argType: ActionArgType): String {
    return when (argType) {
        ActionArgType.INTEGER -> "0"
        ActionArgType.DECIMAL -> "0.0"
        ActionArgType.STRING -> "\"value\""
        ActionArgType.VARIABLE_STRING, ActionArgType.VARIABLE_LIST -> "\$"
    }
}

/** Completions offered inside a rule's `then` block. */
internal fun buildActionNameCompletions(actionSchema: ActionSchema?): List<CompletionItem> {
    val schema = actionSchema ?: return emptyList()
    return schema.actions.map { (name, def) ->
        val argPlaceholders = def.argTypes.joinToString(separator = " ") { argType ->
            placeholderFor(argType = argType)
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

/**
 * A placeholder per declared argument, for the author to type over.
 *
 * A declared *variable* argument contributes none: the variables themselves are offered there, and a
 * bare `$` next to `$topics` in the same list is noise rather than a choice.
 */
internal fun buildActionArgCompletions(
    actionName: String,
    actionSchema: ActionSchema?
): List<CompletionItem> {
    val def = actionSchema?.actions?.get(actionName) ?: return emptyList()
    return def.argTypes.mapIndexedNotNull { idx, argType ->
        if (argType.isVariableReference()) {
            return@mapIndexedNotNull null
        }
        val placeholder = placeholderFor(argType = argType)
        CompletionItem(
            label = placeholder,
            insertText = placeholder,
            kind = CompletionKind.LITERAL,
            hint = "arg${idx + 1}: ${argType.name.lowercase()}"
        )
    }
}

/**
 * Which kind of variable the action's argument at [index] is declared to take, or null when the action
 * declares a literal type — or none at all.
 *
 * Null is what keeps the undeclared case unchanged: every variable stays on offer, because an action
 * declaring `string` has always accepted one and the engine still does not check it.
 */
internal fun declaredVariableArgType(
    actionName: String,
    actionSchema: ActionSchema?,
    index: Int = 0,
): ActionArgType? {
    val declared = actionSchema?.actions?.get(actionName)?.argTypes?.getOrNull(index = index)
    return declared?.takeIf { argType -> argType.isVariableReference() }
}
