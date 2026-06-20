package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.components.SectionTitle
import ui.components.StatusBadge

/**
 * Read-only inspector panel for a selected rule.
 * Shows validation status and basic metadata.
 */
@Composable
fun RuleInspector(
    rule: CatalogRule,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "RULE")
        Text(
            text = rule.id,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
        )
        Divider()

        InspectorRow(label = "Status", value = rule.status.label)
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
