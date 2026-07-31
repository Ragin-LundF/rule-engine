package ui.editor.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionDefinition
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldType
import ui.AccentGreen
import ui.AccentPurple
import ui.BgHover
import ui.BorderColor
import ui.ColorAction
import ui.ColorNumber
import ui.ColorString
import ui.DslCursorContext
import ui.DslSection
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary

enum class ViewMode {
    BUILDER,
    CODE,
    DIAGRAM,
    TEST,
    TABLE,
}

fun dslLineOpensBlock(trimmedLine: String): Boolean {
    return trimmedLine.endsWith(char = '{') || trimmedLine == "when" || trimmedLine == "then"
}

fun autoClosingBraceDedent(text: String, bracePos: Int): Pair<String, Int> {
    val lineStart = text.lastIndexOf(char = '\n', startIndex = bracePos - 1) + 1
    val lineContent = text.substring(startIndex = lineStart, endIndex = bracePos)
    if (lineContent.isEmpty() || !lineContent.all { it == ' ' }) {
        return Pair(text, 0)
    }
    val spacesToRemove = lineContent.length.coerceAtMost(maximumValue = 4)
    val newText = text.substring(
        0, lineStart
    ) + lineContent.drop(n = spacesToRemove) + text.substring(startIndex = bracePos)
    return Pair(first = newText, second = spacesToRemove)
}

fun isContextuallyImmediate(context: DslCursorContext): Boolean {
    val expectsOperator = context.section == DslSection.WHEN &&
            context.precedingField != null && context.precedingOperator == null
    val expectsAction = context.section == DslSection.THEN && context.afterAction == null
    return expectsOperator || expectsAction
}

private fun fieldTypeColor(type: FieldType): Color {
    return when (type) {
        FieldType.INTEGER -> PrimaryBlue
        FieldType.DECIMAL -> PrimaryBlue
        FieldType.TEXT -> TextPrimary
        FieldType.BOOLEAN -> AccentPurple
        FieldType.STRING_SET -> AccentGreen
        FieldType.DATE -> TextSecondary
        FieldType.COLLECTION, FieldType.OBJECT -> AccentGreen
    }
}

@Composable
fun ViewModeToggle(
    current: ViewMode,
    onChange: (ViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp)),
    ) {
        ViewModeTab(
            label = "Builder",
            icon = "⊞",
            selected = current == ViewMode.BUILDER,
            onClick = { onChange(ViewMode.BUILDER) },
        )
        Box(Modifier.width(1.dp).height(28.dp).background(BorderColor))
        ViewModeTab(
            label = "Code",
            icon = "{ }",
            selected = current == ViewMode.CODE,
            onClick = { onChange(ViewMode.CODE) },
        )
        Box(Modifier.width(1.dp).height(28.dp).background(BorderColor))
        ViewModeTab(
            label = "Diagram",
            icon = "⬡",
            selected = current == ViewMode.DIAGRAM,
            onClick = { onChange(ViewMode.DIAGRAM) },
        )
        Box(Modifier.width(1.dp).height(28.dp).background(BorderColor))
        ViewModeTab(
            label = "Test",
            icon = "▷",
            selected = current == ViewMode.TEST,
            onClick = { onChange(ViewMode.TEST) },
        )
        Box(Modifier.width(1.dp).height(28.dp).background(BorderColor))
        ViewModeTab(
            label = "Table",
            icon = "▦",
            selected = current == ViewMode.TABLE,
            onClick = { onChange(ViewMode.TABLE) },
        )
    }
}

@Composable
private fun ViewModeTab(
    label: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) ui.BgElevated else Color.Transparent
    val color = if (selected) PrimaryBlue else TextSecondary
    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 5.dp))
            .background(color = bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text = icon, style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = color))
        Text(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
            ),
        )
    }
}

@Composable
fun FieldItem(id: FieldId, def: FieldDefinition, onInsert: (String) -> Unit) {
    val tc = fieldTypeColor(def.type)
    val displayName = def.alias ?: id.value
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.body1,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 3.dp))
                    .background(color = BgHover)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 3.dp))
                    .clickable { onInsert(displayName) }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(text = "⤵", style = MaterialTheme.typography.caption, color = TextMuted)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 3.dp))
                    .background(color = tc.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(text = def.type.name.lowercase(), style = MaterialTheme.typography.caption, color = tc)
            }
        }
        if (def.operators.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                def.operators.forEach { op ->
                    val opText = op.value
                    Box(
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(size = 3.dp))
                            .background(color = BgHover)
                            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 3.dp))
                            .clickable {
                                val ph = when (def.type) {
                                    FieldType.TEXT -> " \"value\""
                                    FieldType.STRING_SET -> " [\"a\", \"b\"]"
                                    FieldType.INTEGER -> " 0"
                                    FieldType.DECIMAL -> " 0.0"
                                    FieldType.BOOLEAN -> " true"
                                    FieldType.DATE -> " \"2024-01-01\""
                                    FieldType.COLLECTION, FieldType.OBJECT -> ""
                                }
                                onInsert("$displayName $opText$ph")
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(text = opText, style = MaterialTheme.typography.caption, color = TextSecondary)
                    }
                }
            }
        }
        Divider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ActionItem(name: String, def: ActionDefinition, onInsert: (String) -> Unit) {
    val argColor: (ActionArgType) -> Color = { t ->
        when (t) {
            ActionArgType.STRING -> ColorString
            ActionArgType.INTEGER -> ColorNumber
            ActionArgType.DECIMAL -> ColorNumber
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.body1,
                color = ColorAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(3.dp))
                    .background(color = BgHover)
                    .border(1.dp, BorderColor, RoundedCornerShape(size = 3.dp))
                    .clickable {
                        val args = def.argTypes.joinToString(separator = " ") { argType ->
                            when (argType) {
                                ActionArgType.INTEGER -> "0"
                                ActionArgType.DECIMAL -> "0.0"
                                ActionArgType.STRING -> "\"value\""
                            }
                        }
                        onInsert(if (args.isNotEmpty()) "$name $args" else name)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(text = "⤵", style = MaterialTheme.typography.caption, color = TextMuted)
            }
        }
        if (def.argTypes.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                def.argTypes.forEachIndexed { index, argType ->
                    val ac = argColor(argType)
                    Box(
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(size = 3.dp))
                            .background(color = ac.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "arg${index + 1}: ${argType.name.lowercase()}",
                            style = MaterialTheme.typography.caption,
                            color = ac,
                        )
                    }
                }
            }
        }
        Divider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}



