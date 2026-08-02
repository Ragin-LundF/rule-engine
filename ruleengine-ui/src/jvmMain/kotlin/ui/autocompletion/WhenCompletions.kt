package ui.autocompletion

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldSchema
import ui.autocompletion.model.CompletionItem
import ui.autocompletion.model.CompletionKind
import ui.builder.OperatorOptions

/**
 * A worked example per function, and the word shown beside it.
 *
 * Hand-written rather than generated: the point of a completion is the realistic call it inserts,
 * and no registry knows that `daysBetween` wants two dates while `sum` wants a projected collection.
 * `AutoCompleteTest` checks that this table covers every function the engine knows, so a function
 * added to the registry cannot stay invisible here.
 */
private val FUNCTION_COMPLETIONS: List<Triple<String, String, String>> = listOf(
    Triple("count", "count(transactions)", "aggregate"),
    Triple("sum", "sum(transactions.amount)", "aggregate"),
    Triple("subtract", "subtract(transactions.amount)", "aggregate"),
    Triple("avg", "avg(transactions.amount)", "aggregate"),
    Triple("median", "median(transactions.amount)", "aggregate"),
    Triple("max", "max(transactions.amount)", "aggregate"),
    Triple("min", "min(transactions.amount)", "aggregate"),
    // Not aggregates: they take values rather than reducing a collection, so they are hinted
    // differently and are absent from the Builder's aggregate picker.
    Triple("abs", "abs(sum(transactions.amount))", "function"),
    Triple("daysBetween", "daysBetween(registeredAt, submittedAt)", "function"),
    Triple("take", "take(orders, 3)", "slice"),
    Triple("takeLast", "takeLast(orders, 3)", "slice"),
    Triple("every", "every(orders[total > 0])", "predicate"),
    Triple("any", "any(orders[total > 1000])", "predicate"),
    Triple("sumByKey", "sumByKey(\"month\", sales.amount, refunds.amount)", "join"),
)

/** Completions offered inside a rule's `when` block. */
internal fun buildAggregateFunctionCompletions(): List<CompletionItem> {
    return FUNCTION_COMPLETIONS.map { (name, insertText, hint) ->
        CompletionItem(
            label = "$name(...)",
            insertText = insertText,
            kind = CompletionKind.OPERATOR,
            hint = hint,
        )
    }
}

internal fun buildWhenGeneralCompletions(schema: FieldSchema?): List<CompletionItem> {
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

internal fun buildOperatorCompletions(
    fieldName: String,
    schema: FieldSchema?
): List<CompletionItem> {
    if (fieldName.startsWith(prefix = "$")) {
        return variableOperatorCompletions(variableName = fieldName)
    }
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

/**
 * Operators for a `${'$'}variable` operand, which resolves to no schema field.
 *
 * A variable carries whatever the assigning clause produced, and which clause that was is not known
 * here, so both shapes are offered: the symbolic comparisons a `set` value takes, and `contains` for
 * a list built by `add`.
 */
private fun variableOperatorCompletions(variableName: String): List<CompletionItem> {
    val operators = OperatorOptions.COMPARISON_NUMERIC + OperatorOptions.CONTAINS
    return operators.map { op ->
        CompletionItem(
            label = "$op $variableName",
            insertText = "$variableName $op ",
            kind = CompletionKind.OPERATOR,
            hint = "variable"
        )
    }
}

internal fun buildValuePlaceholderCompletions(
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
