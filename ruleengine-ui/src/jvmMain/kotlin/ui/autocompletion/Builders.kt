package ui.autocompletion

import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.dsl.ast.AssignmentKindAst
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
/**
 * Forwarding function for contextual completions.
 *
 * [variableNames] are the rule output variables the open file publishes, without the `$` prefix.
 * They are passed in rather than derived here because this module never parses — the caller already
 * holds the parsed buffer.
 */
public fun buildContextualCompletions(
    context: DslCursorContext,
    schema: FieldSchema?,
    actionSchema: ActionSchema?,
    variableNames: List<String> = emptyList(),
    variableKinds: Map<String, AssignmentKindAst> = emptyMap(),
): List<CompletionItem> {
    return when (context.section) {
        DslSection.TOP_LEVEL -> buildTopLevelCompletions()
        DslSection.RULE_HEADER -> buildRuleHeaderCompletions()
        DslSection.WHEN -> buildWhenCompletions(
            context = context,
            schema = schema
        ) + buildVariableCompletions(variableNames = variableNames)
        // Every branch takes the same clauses, so all get the same completions — they differ only in
        // which branch keyword may still follow, since the blocks are written in a fixed order.
        DslSection.THEN -> buildBranchCompletions(
            context = context,
            actionSchema = actionSchema,
            variableNames = variableNames,
            variableKinds = variableKinds,
            followingBranchKeywords = listOf(ELSE_KEYWORD_COMPLETION, NOT_EXISTS_KEYWORD_COMPLETION),
        )

        DslSection.ELSE -> buildBranchCompletions(
            context = context,
            actionSchema = actionSchema,
            variableNames = variableNames,
            variableKinds = variableKinds,
            followingBranchKeywords = listOf(NOT_EXISTS_KEYWORD_COMPLETION),
        )

        DslSection.NOT_EXISTS -> buildBranchCompletions(
            context = context,
            actionSchema = actionSchema,
            variableNames = variableNames,
            variableKinds = variableKinds,
            followingBranchKeywords = emptyList(),
        )
    }
}

/**
 * The variables worth offering as this action's argument.
 *
 * Narrowed only when the action *declares* a variable type and the kinds are known: `variable_list`
 * takes a name written with `add`, `variable_string` one written with `set`, and offering the other kind
 * would complete straight into a validation error. Everything else offers every variable, which is what
 * it has always done — an action declaring `string` still accepts one, unchecked.
 */
private fun variablesFittingArgument(
    actionName: String,
    actionSchema: ActionSchema?,
    variableNames: List<String>,
    variableKinds: Map<String, AssignmentKindAst>,
): List<String> {
    val declared = declaredVariableArgType(actionName = actionName, actionSchema = actionSchema)
        ?: return variableNames
    if (variableKinds.isEmpty()) {
        return variableNames
    }
    val wanted = if (declared == ActionArgType.VARIABLE_LIST) AssignmentKindAst.ADD else AssignmentKindAst.SET
    return variableNames.filter { name -> variableKinds[name] == wanted }
}

/** A `$name` entry per known variable, offered wherever a value can stand. */
private fun buildVariableCompletions(variableNames: List<String>): List<CompletionItem> {
    return variableNames.map { name ->
        CompletionItem(
            label = "\$$name",
            insertText = "\$$name",
            kind = CompletionKind.FIELD,
            hint = "variable"
        )
    }
}

/** A bare name per known variable, for the target of an `add` clause. */
private fun buildListNameCompletions(variableNames: List<String>): List<CompletionItem> {
    return variableNames.map { name ->
        CompletionItem(
            label = name,
            insertText = name,
            kind = CompletionKind.FIELD,
            hint = "list variable"
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

/**
 * The completions of one output branch.
 *
 * [followingBranchKeywords] is what the section is still allowed to be followed by: a `then` block may
 * open an `else` or a `not_exists`, an `else` block only a `not_exists`, and a `not_exists` block
 * nothing — offering a keyword the parser would reject at that position is worse than offering none.
 */
private fun buildBranchCompletions(
    context: DslCursorContext,
    actionSchema: ActionSchema?,
    variableNames: List<String>,
    variableKinds: Map<String, AssignmentKindAst>,
    followingBranchKeywords: List<CompletionItem>,
): List<CompletionItem> {
    // The target of an `add` is written bare, without the `$`, so neither an action argument nor a
    // `$name` reference is what belongs here.
    if (context.expectsListName) {
        return buildListNameCompletions(variableNames = variableNames)
    }

    if (context.afterAction != null) {
        return buildActionArgCompletions(
            actionName = context.afterAction,
            actionSchema = actionSchema
        ) + buildVariableCompletions(
            variableNames = variablesFittingArgument(
                actionName = context.afterAction,
                actionSchema = actionSchema,
                variableNames = variableNames,
                variableKinds = variableKinds,
            )
        )
    }

    val keywords = listOf(
        SET_KEYWORD_COMPLETION,
        ADD_KEYWORD_COMPLETION,
        STOP_KEYWORD_COMPLETION,
    ) + followingBranchKeywords
    return buildActionNameCompletions(actionSchema = actionSchema) + keywords
}

private val SET_KEYWORD_COMPLETION = CompletionItem(
    label = "set",
    insertText = "set name = ",
    kind = CompletionKind.KEYWORD,
    hint = "publish a variable for later rules"
)

private val ADD_KEYWORD_COMPLETION = CompletionItem(
    label = "add",
    insertText = "add \"\" to name",
    kind = CompletionKind.KEYWORD,
    hint = "append a value to a list variable"
)

private val ELSE_KEYWORD_COMPLETION = CompletionItem(
    label = "else",
    insertText = "else",
    kind = CompletionKind.KEYWORD,
    hint = "output when the condition does not hold"
)

private val NOT_EXISTS_KEYWORD_COMPLETION = CompletionItem(
    label = "not_exists",
    insertText = "not_exists",
    kind = CompletionKind.KEYWORD,
    hint = "output when the record carries no data to decide the condition"
)

private val STOP_KEYWORD_COMPLETION = CompletionItem(
    label = "stop",
    insertText = "stop",
    kind = CompletionKind.KEYWORD,
    hint = "end the run — no rule after this one is evaluated"
)
