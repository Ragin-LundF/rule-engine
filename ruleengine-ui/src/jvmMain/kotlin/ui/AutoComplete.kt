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
import ruleengine.core.domain.FieldSchema

// ── Completion model ──────────────────────────────────────────────────────────

enum class CompletionKind { KEYWORD, LOGIC, FIELD, ACTION, LITERAL }

data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val hint: String = "",
)

/** Build the full completion list from static keywords + loaded schema / actions. */
fun buildAllCompletions(schema: FieldSchema?, actionSchema: ActionSchema?): List<CompletionItem> =
    buildList {
        // DSL structure keywords
        add(CompletionItem("rule",  "rule \"\"",       CompletionKind.KEYWORD, "keyword"))
        add(CompletionItem("when",  "when",             CompletionKind.KEYWORD, "keyword"))
        add(CompletionItem("then",  "then",             CompletionKind.KEYWORD, "keyword"))
        // Logic operators
        add(CompletionItem("and",   "and",              CompletionKind.LOGIC,   "logic"))
        add(CompletionItem("or",    "or",               CompletionKind.LOGIC,   "logic"))
        add(CompletionItem("not",   "not",              CompletionKind.LOGIC,   "logic"))
        // Boolean literals
        add(CompletionItem("true",  "true",             CompletionKind.LITERAL, "boolean"))
        add(CompletionItem("false", "false",            CompletionKind.LITERAL, "boolean"))
        // Schema fields
        schema?.fields?.forEach { (id, def) ->
            // Insert bare field name; operator + value will be typed by the user
            add(CompletionItem(id.value, id.value, CompletionKind.FIELD, def.type.name.lowercase()))
        }
        // Action names
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
    CompletionKind.KEYWORD -> ColorKeyword
    CompletionKind.LOGIC   -> ColorLogic
    CompletionKind.FIELD   -> ColorField
    CompletionKind.ACTION  -> ColorAction
    CompletionKind.LITERAL -> ColorNumber
}

private fun kindLabel(kind: CompletionKind): String = when (kind) {
    CompletionKind.KEYWORD -> "kw"
    CompletionKind.LOGIC   -> "op"
    CompletionKind.FIELD   -> "field"
    CompletionKind.ACTION  -> "action"
    CompletionKind.LITERAL -> "lit"
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
                        text      = kindLabel(item.kind),
                        style     = TextStyle(
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
                // Tab hint on selected item
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

