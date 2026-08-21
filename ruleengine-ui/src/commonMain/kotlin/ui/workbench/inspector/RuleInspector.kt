package ui.workbench.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
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
@Composable
fun RuleInspector(
    rule: CatalogRule,
    conditionCount: Int = 0,
    actionCount: Int = 0,
    /** Actions in the rule's `else` block. Zero when it declares none, which hides the row. */
    elseActionCount: Int = 0,
    /** How many actions the rule's `not_exists` block declares. Zero when it declares no such block. */
    notExistsActionCount: Int = 0,
    /** Names of the variables this rule publishes with `set`, without the `$` prefix. */
    variableNames: List<String> = emptyList(),
    /**
     * Diagnostics belonging to *this* rule, already filtered by the caller — the panel has no way to
     * tell one rule's line from another's, and an unfiltered list made every rule report the whole
     * buffer's errors as its own.
     */
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

        if (notExistsActionCount > 0) {
            InspectorRow(label = "Not-exists actions", value = notExistsActionCount.toString())
        }

        // Listed by name rather than counted: which variables a rule publishes is what decides what
        // the rules after it can read, and a bare number would not say that.
        if (variableNames.isNotEmpty()) {
            InspectorRow(
                label = "Sets",
                value = variableNames.joinToString(separator = ", ") { name -> "\$$name" },
            )
        }

        RuleDiagnostics(diagnostics = diagnostics)
    }
}

/**
 * The diagnostics of the inspected rule, or nothing when it has none.
 *
 * Capped at three: the panel is 320 dp wide and the diagnostics list at the bottom of the window is
 * where the full set belongs. Its own composable rather than a block inside [RuleInspector] because
 * it is the one part of that panel with a rule of its own — everything above it is a labelled row.
 */
@Composable
private fun RuleDiagnostics(diagnostics: List<UiDiagnostic>) {
    if (diagnostics.isEmpty()) return

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
