package ui.workbench.inspector

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
import ruleengine.core.errors.Severity
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.TextSecondary
import ui.components.SectionTitle
import ui.components.StatusBadge
import ui.workbench.model.UiDiagnostic
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.catalog.CatalogRuleStatus

/**
 * Inspector for a selected parsed rule.
 */
// 71 lines against a threshold of 60. A single Column of labelled rows describing one rule; the
// length is the number of facts shown, not nesting. Every candidate split here would be "the first
// half" and "the second half", which names nothing.
@Suppress("LongMethod")
@Composable
fun RuleInspector(
    rule: CatalogRule,
    conditionCount: Int = 0,
    actionCount: Int = 0,
    /** Actions in the rule's `else` block. Zero when it declares none, which hides the row. */
    elseActionCount: Int = 0,
    /** Names of the variables this rule publishes with `set`, without the `$` prefix. */
    variableNames: List<String> = emptyList(),
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

        // Shown only for a rule that has a false branch, so the panel does not tell every other rule
        // that it has zero of something it cannot have.
        if (elseActionCount > 0) {
            InspectorRow(label = "Else actions", value = elseActionCount.toString())
        }

        // Listed by name rather than counted: which variables a rule publishes is what decides what
        // the rules after it can read, and a bare number would not say that.
        if (variableNames.isNotEmpty()) {
            InspectorRow(
                label = "Sets",
                value = variableNames.joinToString(separator = ", ") { name -> "\$$name" },
            )
        }

        if (diagnostics.isNotEmpty()) {
            Divider()
            SectionTitle(text = "DIAGNOSTICS")
            diagnostics.take(n = 3).forEach { diagnostic ->
                Text(
                    text = "• ${diagnostic.message}",
                    style = MaterialTheme.typography.caption,
                    color = when (diagnostic.severity) {
                        Severity.ERROR -> AccentRed
                        Severity.WARNING -> AccentOrange
                        Severity.INFO -> TextSecondary
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
