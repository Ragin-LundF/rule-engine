package ui.autocompletion

import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.FieldSchema
import ui.DslCursorContext
import ui.DslSection

private fun FieldDefinition.getDisplayId(): String {
    return alias ?: id.value
}

/**
 * Resolves a field identifier from user input to the actual field definition.
 *
 * Delegates to the core resolver, so a nested path (`shipment.customer.tier`) offers the same operator and
 * value completions as a top-level field or an alias.
 */
private fun resolveFieldByIdentifier(
    identifier: String,
    schema: FieldSchema?
): FieldDefinition? {
    val resolution = FieldPathResolver.resolve(
        identifier = identifier,
        schema = schema ?: return null
    )
    return (resolution as? FieldPathResolution.Resolved)?.definition
}

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

private fun buildAggregateFunctionCompletions(): List<CompletionItem> {
    return listOf(
        CompletionItem(label = "count(...)", insertText = "count(transactions)", kind = CompletionKind.OPERATOR, hint = "aggregate"),
        CompletionItem(label = "sum(...)", insertText = "sum(transactions.amount)", kind = CompletionKind.OPERATOR, hint = "aggregate"),
        CompletionItem(label = "subtract(...)", insertText = "subtract(transactions.amount)", kind = CompletionKind.OPERATOR, hint = "aggregate"),
        CompletionItem(label = "avg(...)", insertText = "avg(transactions.amount)", kind = CompletionKind.OPERATOR, hint = "aggregate"),
        CompletionItem(label = "median(...)", insertText = "median(transactions.amount)", kind = CompletionKind.OPERATOR, hint = "aggregate"),
        CompletionItem(label = "max(...)", insertText = "max(transactions.amount)", kind = CompletionKind.OPERATOR, hint = "aggregate"),
        CompletionItem(label = "min(...)", insertText = "min(transactions.amount)", kind = CompletionKind.OPERATOR, hint = "aggregate"),
    )
}

private fun buildWhenGeneralCompletions(schema: FieldSchema?): List<CompletionItem> {
    return buildList {
        add(CompletionItem(label = "and", insertText = "and", kind = CompletionKind.LOGIC, hint = "logic"))
        add(CompletionItem(label = "or", insertText = "or", kind = CompletionKind.LOGIC, hint = "logic"))
        add(CompletionItem(label = "not", insertText = "not", kind = CompletionKind.LOGIC, hint = "logic"))
        add(CompletionItem(label = "true", insertText = "true", kind = CompletionKind.LITERAL, hint = "boolean"))
        add(CompletionItem(label = "false", insertText = "false", kind = CompletionKind.LITERAL, hint = "boolean"))
        addAll(buildAggregateFunctionCompletions())
        val offered = mutableSetOf<String>()
        schema?.fields?.forEach { (_, def) ->
            val label = def.getDisplayId()
            offered += label
            add(
                CompletionItem(
                    label = label,
                    insertText = label,
                    kind = CompletionKind.FIELD,
                    hint = def.type.name.lowercase()
                )
            )
        }
        // Members of an `object` are usable in a plain condition, so offer their dotted paths as well.
        schema?.let { loaded ->
            FieldPathResolver.scalarPaths(schema = loaded).forEach { (id, def) ->
                if (offered.add(id.value)) {
                    add(
                        CompletionItem(
                            label = id.value,
                            insertText = id.value,
                            kind = CompletionKind.FIELD,
                            hint = def.type.name.lowercase()
                        )
                    )
                }
            }
        }
    }
}

private fun buildOperatorCompletions(
    fieldName: String,
    schema: FieldSchema?
): List<CompletionItem> {
    val def = resolveFieldByIdentifier(
        identifier = fieldName,
        schema = schema
    ) ?: return emptyList()
    val operators = if (def.operators.isNotEmpty()) {
        def.operators.map { it.value }
    } else {
        defaultOperatorsForType(fieldType = def.type)
    }

    return operators.map { op ->
        val placeholder = valuePlaceholderForOperator(
            op = op,
            def = def
        )
        val displayId = def.getDisplayId()
        CompletionItem(
            label = "$op $displayId",
            insertText = "$displayId $op $placeholder",
            kind = CompletionKind.OPERATOR,
            hint = def.type.name.lowercase()
        )
    }
}

private fun buildValuePlaceholderCompletions(
    fieldName: String,
    operator: String,
    schema: FieldSchema?
): List<CompletionItem> {
    val def = resolveFieldByIdentifier(
        identifier = fieldName,
        schema = schema
    ) ?: return emptyList()
    val placeholder = valuePlaceholderForOperator(
        op = operator,
        def = def
    )
    return listOf(
        CompletionItem(
            label = "${def.getDisplayId()} $placeholder",
            insertText = "${def.getDisplayId()} $operator $placeholder",
            kind = CompletionKind.LITERAL,
            hint = def.type.name.lowercase()
        )
    )
}

private fun buildActionNameCompletions(actionSchema: ActionSchema?): List<CompletionItem> {
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

private fun buildActionArgCompletions(
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

/** Legacy full-completion builder retained for fallback (public API). */
public fun buildAllCompletions(
    schema: FieldSchema?,
    actionSchema: ActionSchema?
): List<CompletionItem> {
    return buildList {
        add(CompletionItem(label = "rule", insertText = "rule \"\"", kind = CompletionKind.KEYWORD, hint = "keyword"))
        add(CompletionItem(label = "when", insertText = "when", kind = CompletionKind.KEYWORD, hint = "keyword"))
        add(CompletionItem(label = "then", insertText = "then", kind = CompletionKind.KEYWORD, hint = "keyword"))
        add(CompletionItem(label = "and", insertText = "and", kind = CompletionKind.LOGIC, hint = "logic"))
        add(CompletionItem(label = "or", insertText = "or", kind = CompletionKind.LOGIC, hint = "logic"))
        add(CompletionItem(label = "not", insertText = "not", kind = CompletionKind.LOGIC, hint = "logic"))
        add(CompletionItem(label = "true", insertText = "true", kind = CompletionKind.LITERAL, hint = "boolean"))
        add(CompletionItem(label = "false", insertText = "false", kind = CompletionKind.LITERAL, hint = "boolean"))
        addAll(buildAggregateFunctionCompletions())
        schema?.fields?.forEach { (id, def) ->
            add(
                CompletionItem(
                    label = def.getDisplayId(),
                    insertText = def.getDisplayId(),
                    kind = CompletionKind.FIELD,
                    hint = def.type.name.lowercase()
                )
            )
        }
        actionSchema?.actions?.forEach { (name, def) ->
            val argPh = def.argTypes.joinToString(separator = " ") { t ->
                when (t) {
                    ActionArgType.INTEGER -> "0"
                    ActionArgType.DECIMAL -> "0.0"
                    else -> "\"value\""
                }
            }
            val insertText = if (argPh.isNotEmpty()) "$name $argPh" else name
            add(
                CompletionItem(
                    label = name,
                    insertText = insertText,
                    kind = CompletionKind.ACTION,
                    hint = def.argTypes.joinToString(separator = ", ") { it.name.lowercase() }
                )
            )
        }
    }
}
