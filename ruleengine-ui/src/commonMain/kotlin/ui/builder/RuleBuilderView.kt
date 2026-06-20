package ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.TextSecondary

/**
 * Read-only visual representation of a single rule in Builder mode.
 *
 * Renders WHEN / AND / THEN blocks derived from [rule].
 * Falls back to a friendly message for unsupported syntax.
 */
@Composable
fun RuleBuilderView(rule: BuilderRule, modifier: Modifier = Modifier) {
    when (rule) {
        is BuilderRule.None -> {
            Text(
                text = "Select a rule from the left panel to preview it here.",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
                modifier = modifier.padding(24.dp),
            )
        }
        is BuilderRule.Unsupported -> {
            Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "⚠  Advanced syntax detected",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface,
                )
                Text(
                    text = rule.reason,
                    style = MaterialTheme.typography.body2,
                    color = TextSecondary,
                )
                Text(
                    text = "Switch to Code mode to edit this rule.",
                    style = MaterialTheme.typography.body2,
                    color = TextSecondary,
                )
            }
        }
        is BuilderRule.Supported -> SupportedRuleView(rule = rule, modifier = modifier)
    }
}

@Composable
private fun SupportedRuleView(rule: BuilderRule.Supported, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // WHEN header
        val whenLabel = when (rule.conditionJoin) {
            ConditionJoin.AND, ConditionJoin.SINGLE -> "WHEN  All conditions are met"
            ConditionJoin.OR -> "WHEN  Any condition is met"
        }
        Text(
            text = whenLabel,
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
        )

        // Condition rows
        rule.conditions.forEachIndexed { index, condition ->
            if (index > 0 && rule.conditionJoin == ConditionJoin.AND) {
                Text(
                    text = "AND",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            ConditionBlock(condition = condition)
        }

        // THEN header
        Text(
            text = "THEN",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Action rows
        if (rule.actions.isEmpty()) {
            Text(
                text = "(no actions)",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        } else {
            rule.actions.forEach { action ->
                ActionBlock(action = action)
            }
        }
    }
}
