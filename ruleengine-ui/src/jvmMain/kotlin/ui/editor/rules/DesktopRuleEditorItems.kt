package ui.editor.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextSecondary
import ui.dsl.model.DslCursorContext
import ui.dsl.model.DslSection
import ui.editor.rules.model.ViewMode

private val DSL_BLOCK_KEYWORDS = setOf("when", "then", "else")

fun dslLineOpensBlock(trimmedLine: String): Boolean {
    return trimmedLine.endsWith(char = '{') || trimmedLine in DSL_BLOCK_KEYWORDS
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
    val inBranch = context.section == DslSection.THEN || context.section == DslSection.ELSE
    val expectsAction = inBranch && context.afterAction == null
    return expectsOperator || expectsAction
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
