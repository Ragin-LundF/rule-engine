package ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.components.SectionTitle
import ui.components.StatusBadge

/**
 * A compact list of rule entries with status badges.
 * Clicking a rule row notifies the caller so the editor selection can be updated.
 */
@Composable
fun RuleListSection(
    rules: List<CatalogRule>,
    selectedRuleId: String?,
    onRuleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        SectionTitle(text = "RULES")
        rules.forEach { rule ->
            RuleRow(
                rule = rule,
                selected = rule.id == selectedRuleId,
                onClick = { onRuleClick(rule.id) },
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: CatalogRule,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) PrimaryBlue.copy(alpha = 0.12f) else BgElevated
    val border = if (selected) PrimaryBlue.copy(alpha = 0.45f) else BorderColor
    val nameColor = if (selected) PrimaryBlue else TextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = bg)
            .border(
                width = 1.dp,
                color = border,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = rule.id,
            style = MaterialTheme.typography.body2.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = nameColor,
            modifier = Modifier.weight(weight = 1f),
        )
        StatusBadge(
            label = rule.status.label,
            color = when (rule.status) {
                CatalogRuleStatus.VALID -> AccentGreen
                CatalogRuleStatus.INVALID -> AccentRed
                CatalogRuleStatus.DRAFT -> AccentOrange
            },
        )
    }
}

/**
 * A rule entry for display in the catalog list.
 */
data class CatalogRule(
    val id: String,
    val status: CatalogRuleStatus = CatalogRuleStatus.DRAFT,
)

enum class CatalogRuleStatus(val label: String) {
    VALID("valid"),
    INVALID("invalid"),
    DRAFT("draft"),
}
