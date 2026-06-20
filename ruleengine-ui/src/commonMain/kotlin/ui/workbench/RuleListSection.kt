package ui.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = rule.id,
            style = MaterialTheme.typography.body2,
            color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
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
