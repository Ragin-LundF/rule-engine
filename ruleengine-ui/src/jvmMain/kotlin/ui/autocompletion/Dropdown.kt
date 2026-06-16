package ui.autocompletion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary

/** Floating dropdown showing autocomplete suggestions. */
@Composable
public fun AutoCompleteDropdown(
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
                    .background(if (isSelected) BgHover else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(kindColor(item.kind).copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = kindLabel(item.kind),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = kindColor(item.kind)
                        ),
                    )
                }
                Text(
                    text = item.label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (item.hint.isNotEmpty()) {
                    Text(
                        text = item.hint,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    )
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text("tab", style = TextStyle(fontSize = 9.sp, color = TextMuted))
                    }
                }
            }
        }
    }
}


