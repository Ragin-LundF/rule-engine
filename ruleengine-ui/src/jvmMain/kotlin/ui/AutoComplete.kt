package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType

// ── Completion model ──────────────────────────────────────────────────────────

enum class CompletionKind { KEYWORD, LOGIC, FIELD, ACTION, LITERAL, OPERATOR }

data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val hint: String = "",
)

// ── Operator/value lookup tables ──────────────────────────────────────────────

private val TEXT_OPS    = listOf("equals", "contains", "startsWith", "endsWith", "in", "regex")
private val NUM_OPS     = listOf("equals", "gt", "gte", "lt", "lte", "between")
private val BOOL_OPS    = listOf("equals")
private val SET_OPS     = listOf("contains", "containsAny", "containsAll")
private val DATE_OPS    = listOf("equals", "gt", "gte", "lt", "lte", "between")

private fun defaultOperatorsForType(fieldType: FieldType): List<String> {
    return when (fieldType) {
        FieldType.TEXT       -> TEXT_OPS
        FieldType.INTEGER    -> NUM_OPS
        FieldType.DECIMAL    -> NUM_OPS
        FieldType.BOOLEAN    -> BOOL_OPS
        FieldType.STRING_SET -> SET_OPS
        FieldType.DATE       -> DATE_OPS
    }
}

private fun valuePlaceholderForOperator(op: String, fieldType: FieldType): String {
    return when (op.lowercase()) {
        "between"                    -> "0 100"
        "in", "containsany", "containsall" -> "[\"a\", \"b\"]"
        else                         -> when (fieldType) {
            FieldType.TEXT       -> "\"value\""
            FieldType.INTEGER    -> "0"
            FieldType.DECIMAL    -> "0.0"
            FieldType.BOOLEAN    -> "true"
            FieldType.STRING_SET -> "\"value\""
            FieldType.DATE       -> "\"2024-01-01\""
        }
    }
}

// ── Context-aware completion builder ──────────────────────────────────────────

/**
 * Builds a list of completion items that are relevant for the given [context].
 * This replaces the flat [buildAllCompletions] when the cursor context is known.
 */
fun buildContextualCompletions(
    context: DslCursorContext,
    schema: FieldSchema?,
    actionSchema: ActionSchema?,
): List<CompletionItem> {
    return when (context.section) {
        DslSection.TOP_LEVEL   -> buildTopLevelCompletions()
        DslSection.RULE_HEADER -> buildRuleHeaderCompletions()
        DslSection.WHEN        -> buildWhenCompletions(
            context = context,
            schema = schema,
        )
        DslSection.THEN        -> buildThenCompletions(
            context = context,
            actionSchema = actionSchema,
        )
    }
}

private fun buildTopLevelCompletions(): List<CompletionItem> {
    return listOf(
        CompletionItem(
            label = "rule",
            insertText = "rule \"\" {\n  when\n    \n  then\n    \n}",
            kind = CompletionKind.KEYWORD,
            hint = "keyword",
        )
    )
}

private fun buildRuleHeaderCompletions(): List<CompletionItem> {
    return listOf(
        CompletionItem(label = "when",        insertText = "when",           kind = CompletionKind.KEYWORD, hint = "keyword"),
        CompletionItem(label = "then",        insertText = "then",           kind = CompletionKind.KEYWORD, hint = "keyword"),
        CompletionItem(label = "description", insertText = "description \"\"", kind = CompletionKind.KEYWORD, hint = "keyword"),
    )
}

private fun buildWhenCompletions(
    context: DslCursorContext,
    schema: FieldSchema?,
): List<CompletionItem> {
    return when {
        context.precedingField != null && context.precedingOperator == null ->
            buildOperatorCompletions(fieldName = context.precedingField, schema = schema)

        context.precedingField != null && context.precedingOperator != null ->
            buildValuePlaceholderCompletions(
                fieldName = context.precedingField,
                operator = context.precedingOperator,
                schema = schema,
            )

        else -> buildWhenGeneralCompletions(schema = schema)
    }
}

private fun buildThenCompletions(
    context: DslCursorContext,
    actionSchema: ActionSchema?,
): List<CompletionItem> {
    return if (context.afterAction == null) {
        buildActionNameCompletions(actionSchema = actionSchema)
    } else {
        buildActionArgCompletions(actionName = context.afterAction, actionSchema = actionSchema)
    }
}

/** General WHEN-block completions: field names + logic words. */
private fun buildWhenGeneralCompletions(schema: FieldSchema?): List<CompletionItem> {
    return buildList {
        add(CompletionItem(label = "and",   insertText = "and",   kind = CompletionKind.LOGIC,   hint = "logic"))
        add(CompletionItem(label = "or",    insertText = "or",    kind = CompletionKind.LOGIC,   hint = "logic"))
        add(CompletionItem(label = "not",   insertText = "not",   kind = CompletionKind.LOGIC,   hint = "logic"))
        add(CompletionItem(label = "true",  insertText = "true",  kind = CompletionKind.LITERAL, hint = "boolean"))
        add(CompletionItem(label = "false", insertText = "false", kind = CompletionKind.LITERAL, hint = "boolean"))
        schema?.fields?.forEach { (id, def) ->
            add(CompletionItem(
                label = id.value,
                insertText = id.value,
                kind = CompletionKind.FIELD,
                hint = def.type.name.lowercase(),
            ))
        }
    }
}

/** Operator completions for a specific field, derived from the loaded schema. */
private fun buildOperatorCompletions(fieldName: String, schema: FieldSchema?): List<CompletionItem> {
    val def = schema?.fields?.get(FieldId(fieldName)) ?: return emptyList()
    val operators = if (def.operators.isNotEmpty()) {
        def.operators.map { it.value }
    } else {
        defaultOperatorsForType(def.type)
    }
    return operators.map { op ->
        val placeholder = valuePlaceholderForOperator(op = op, fieldType = def.type)
        CompletionItem(
            label = op,
            insertText = "$op $placeholder",
            kind = CompletionKind.OPERATOR,
            hint = def.type.name.lowercase(),
        )
    }
}

/** Value placeholder completion for when field + operator are already typed. */
private fun buildValuePlaceholderCompletions(
    fieldName: String,
    operator: String,
    schema: FieldSchema?,
): List<CompletionItem> {
    val def = schema?.fields?.get(FieldId(fieldName)) ?: return emptyList()
    val placeholder = valuePlaceholderForOperator(op = operator, fieldType = def.type)
    return listOf(
        CompletionItem(
            label = placeholder,
            insertText = placeholder,
            kind = CompletionKind.LITERAL,
            hint = def.type.name.lowercase(),
        )
    )
}

/** Action name completions with argument placeholders inserted alongside. */
private fun buildActionNameCompletions(actionSchema: ActionSchema?): List<CompletionItem> {
    val schema = actionSchema ?: return emptyList()
    return schema.actions.map { (name, def) ->
        val argPlaceholders = def.argTypes.joinToString(separator = " ") { argType ->
            when (argType) {
                ActionArgType.INTEGER -> "0"
                ActionArgType.DECIMAL -> "0.0"
                ActionArgType.STRING  -> "\"value\""
            }
        }
        val insertText = if (argPlaceholders.isNotEmpty()) "$name $argPlaceholders" else name
        CompletionItem(
            label = name,
            insertText = insertText,
            kind = CompletionKind.ACTION,
            hint = def.argTypes.joinToString(", ") { it.name.lowercase() },
        )
    }
}

/** Argument placeholder completions shown when an action name is already on the line. */
private fun buildActionArgCompletions(actionName: String, actionSchema: ActionSchema?): List<CompletionItem> {
    val def = actionSchema?.actions?.get(actionName) ?: return emptyList()
    return def.argTypes.mapIndexed { idx, argType ->
        val placeholder = when (argType) {
            ActionArgType.INTEGER -> "0"
            ActionArgType.DECIMAL -> "0.0"
            ActionArgType.STRING  -> "\"value\""
        }
        CompletionItem(
            label = placeholder,
            insertText = placeholder,
            kind = CompletionKind.LITERAL,
            hint = "arg${idx + 1}: ${argType.name.lowercase()}",
        )
    }
}

// ── Legacy flat completion builder (kept for fallback) ────────────────────────

/** Build the full completion list from static keywords + loaded schema / actions. */
fun buildAllCompletions(schema: FieldSchema?, actionSchema: ActionSchema?): List<CompletionItem> {
    return buildList {
        add(CompletionItem("rule",  "rule \"\"",  CompletionKind.KEYWORD, "keyword"))
        add(CompletionItem("when",  "when",        CompletionKind.KEYWORD, "keyword"))
        add(CompletionItem("then",  "then",        CompletionKind.KEYWORD, "keyword"))
        add(CompletionItem("and",   "and",         CompletionKind.LOGIC,   "logic"))
        add(CompletionItem("or",    "or",          CompletionKind.LOGIC,   "logic"))
        add(CompletionItem("not",   "not",         CompletionKind.LOGIC,   "logic"))
        add(CompletionItem("true",  "true",        CompletionKind.LITERAL, "boolean"))
        add(CompletionItem("false", "false",       CompletionKind.LITERAL, "boolean"))
        schema?.fields?.forEach { (id, def) ->
            add(CompletionItem(id.value, id.value, CompletionKind.FIELD, def.type.name.lowercase()))
        }
        actionSchema?.actions?.forEach { (name, def) ->
            val argPh = def.argTypes.joinToString(" ") { t ->
                when (t) {
                    ActionArgType.INTEGER -> "0"
                    ActionArgType.DECIMAL -> "0.0"
                    else                  -> "\"value\""
                }
            }
            val insertText = if (argPh.isNotEmpty()) "$name $argPh" else name
            add(CompletionItem(name, insertText, CompletionKind.ACTION,
                def.argTypes.joinToString(", ") { it.name.lowercase() }))
        }
    }
}

/**
 * Extract the "word" currently being typed at [cursorPos] in [text].
 * Returns (wordStart, word). Word characters match the DSL lexer: letters, digits, `_`, `-`.
 */
fun extractCurrentWord(text: String, cursorPos: Int): Pair<Int, String> {
    val cursor = cursorPos.coerceIn(0, text.length)
    var wordStart = cursor
    while (wordStart > 0) {
        val ch = text[wordStart - 1]
        if (ch.isLetterOrDigit() || ch == '_' || ch == '-') wordStart-- else break
    }
    return Pair(wordStart, text.substring(wordStart, cursor))
}

// ── UI ────────────────────────────────────────────────────────────────────────

private fun kindColor(kind: CompletionKind): Color = when (kind) {
    CompletionKind.KEYWORD  -> ColorKeyword
    CompletionKind.LOGIC    -> ColorLogic
    CompletionKind.FIELD    -> ColorField
    CompletionKind.ACTION   -> ColorAction
    CompletionKind.LITERAL  -> ColorNumber
    CompletionKind.OPERATOR -> ColorOp
}

private fun kindLabel(kind: CompletionKind): String = when (kind) {
    CompletionKind.KEYWORD  -> "kw"
    CompletionKind.LOGIC    -> "op"
    CompletionKind.FIELD    -> "field"
    CompletionKind.ACTION   -> "action"
    CompletionKind.LITERAL  -> "lit"
    CompletionKind.OPERATOR -> "op"
}

/**
 * A floating dropdown that shows autocomplete [suggestions].
 * The [modifier] should position this via `Modifier.offset(x, y)` in the parent.
 */
@Composable
fun AutoCompleteDropdown(
    suggestions: List<CompletionItem>,
    selectedIndex: Int,
    onSelect: (CompletionItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(260.dp)
            .shadow(8.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(BgElevated)
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
    ) {
        suggestions.forEachIndexed { idx, item ->
            val isSelected = idx == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) BgHover else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Kind badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(kindColor(item.kind).copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text  = kindLabel(item.kind),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color      = kindColor(item.kind),
                        ),
                    )
                }
                // Label
                Text(
                    text     = item.label,
                    style    = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        color      = if (isSelected) TextPrimary else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    modifier = Modifier.weight(1f),
                )
                // Type hint
                if (item.hint.isNotEmpty()) {
                    Text(
                        text  = item.hint,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 10.sp,
                            color      = TextMuted,
                        ),
                    )
                }
                // Tab hint on the selected item
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "tab",
                            style = TextStyle(fontSize = 9.sp, color = TextMuted),
                        )
                    }
                }
            }
        }
    }
}
