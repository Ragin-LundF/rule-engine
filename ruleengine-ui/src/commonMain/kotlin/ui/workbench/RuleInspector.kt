package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.TextSecondary
import ui.components.SectionTitle
import ui.components.StatusBadge

/**
 * Inspector for a selected parsed rule.
 */
@Composable
fun RuleInspector(
    rule: CatalogRule,
    conditionCount: Int = 0,
    actionCount: Int = 0,
    diagnostics: List<UiDiagnostic> = emptyList(),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
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

        InspectorRow(label = "Conditions", value = conditionCount.toString())
        InspectorRow(label = "Actions", value = actionCount.toString())

        if (diagnostics.isNotEmpty()) {
            Divider()
            SectionTitle(text = "DIAGNOSTICS")
            diagnostics.take(n = 3).forEach { diagnostic ->
                Text(
                    text = "• ${diagnostic.message}",
                    style = MaterialTheme.typography.caption,
                    color = when (diagnostic.severity) {
                        UiDiagnosticSeverity.ERROR -> AccentRed
                        UiDiagnosticSeverity.WARNING -> AccentOrange
                        UiDiagnosticSeverity.INFO -> TextSecondary
                    },
                )
            }
        }

        Divider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { /* TODO duplicate rule */ },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Duplicate", style = MaterialTheme.typography.caption)
            }
            OutlinedButton(
                onClick = { /* TODO delete rule */ },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentRed,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Delete", style = MaterialTheme.typography.caption)
            }
        }
    }
}
