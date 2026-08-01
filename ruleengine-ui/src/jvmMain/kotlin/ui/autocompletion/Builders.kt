package ui.autocompletion

import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ui.autocompletion.model.CompletionItem
import ui.autocompletion.model.CompletionKind
import ui.dsl.model.DslCursorContext
import ui.dsl.model.DslSection

/**
 * Resolves a field identifier from user input to the actual field definition.
 *
 * Delegates to the core resolver, so a nested path (`shipment.customer.tier`) offers the same operator and
 * value completions as a top-level field or an alias.
 */
/** Forwarding function for contextual completions. */
public fun buildContextualCompletions(
    context: DslCursorContext,
    schema: FieldSchema?,
    actionSchema: ActionSchema?,
): List<CompletionItem> {
    return when (context.section) {
        DslSection.TOP_LEVEL -> buildTopLevelCompletions()
        DslSection.RULE_HEADER -> buildRuleHeaderCompletions()
        DslSection.WHEN -> buildWhenCompletions(
            context = context,
            schema = schema
        )
        DslSection.THEN -> buildThenCompletions(
            context = context,
            actionSchema = actionSchema
        )
    }
}

private fun buildTopLevelCompletions(): List<CompletionItem> {
    return listOf(
        CompletionItem(
            label = "rule",
            insertText = "rule \"\" {\n  when\n    \n  then\n    \n}",
            kind = CompletionKind.KEYWORD,
            hint = "keyword"
        )
    )
}

private fun buildRuleHeaderCompletions(): List<CompletionItem> {
    return listOf(
        CompletionItem(
            label = "when",
            insertText = "when",
            kind = CompletionKind.KEYWORD,
            hint = "keyword"
        ),
        CompletionItem(
            label = "then",
            insertText = "then",
            kind = CompletionKind.KEYWORD,
            hint = "keyword"
        ),
        CompletionItem(
            label = "description",
            insertText = "description \"\"",
            kind = CompletionKind.KEYWORD,
            hint = "keyword"
        ),
    )
}

private fun buildWhenCompletions(
    context: DslCursorContext,
    schema: FieldSchema?
): List<CompletionItem> {
    return when {
        context.precedingField != null && context.precedingOperator == null ->
            buildOperatorCompletions(
                fieldName = context.precedingField,
                schema = schema
            )

        context.precedingField != null && context.precedingOperator != null ->
            buildValuePlaceholderCompletions(
                fieldName = context.precedingField,
                operator = context.precedingOperator,
                schema = schema
            )

        else -> buildWhenGeneralCompletions(
            schema = schema
        )
    }
}

private fun buildThenCompletions(
    context: DslCursorContext,
    actionSchema: ActionSchema?
): List<CompletionItem> {
    return if (context.afterAction == null) {
        buildActionNameCompletions(
            actionSchema = actionSchema
        )
    } else {
        buildActionArgCompletions(
            actionName = context.afterAction,
            actionSchema = actionSchema
        )
    }
}
