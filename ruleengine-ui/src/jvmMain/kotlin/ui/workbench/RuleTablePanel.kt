package ui.workbench

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
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
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextSecondary
import ui.builder.BuilderAction
import ui.builder.BuilderCondition
import ui.builder.BuilderConditionNode
import ui.builder.BuilderRule
import ui.builder.OperandText
import ui.components.StatusBadge
import ui.workbench.model.CatalogRule
import ui.workbench.model.CatalogRuleStatus

@Suppress("FunctionNaming")
@Composable
fun RuleTablePanel(
    allBuilderRules: List<BuilderRule>,
    catalogRules: List<CatalogRule>,
    selectedRuleId: String,
    onRuleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusById = catalogRules.associateBy { it.id }
    val rows = allBuilderRules.filter { it !is BuilderRule.None }

    Column(modifier = modifier) {
        RuleTableHeader()
        Divider(color = BorderColor, thickness = 1.dp)
        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No rules loaded",
                    style = MaterialTheme.typography.body2,
                    color = TextSecondary,
                )
            }
        } else {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(rows) { rule ->
                        RuleTableRow(
                            rule = rule,
                            status = statusById[rule.tableRuleId()],
                            selected = rule.tableRuleId() == selectedRuleId,
                            onClick = { onRuleClick(rule.tableRuleId()) },
                        )
                        Divider(color = BorderColor, thickness = 1.dp)
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    modifier = Modifier.align(alignment = Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RuleTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RULE ID",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
            modifier = Modifier.weight(weight = 0.22f),
        )
        Text(
            text = "STATUS",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
            modifier = Modifier.width(width = 80.dp),
        )
        Text(
            text = "CONDITIONS",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
            modifier = Modifier.weight(weight = 0.48f),
        )
        Text(
            text = "ACTIONS",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
            modifier = Modifier.weight(weight = 0.30f),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RuleTableRow(
    rule: BuilderRule,
    status: CatalogRule?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowModifier = if (selected) {
        Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = PrimaryBlue.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = PrimaryBlue.copy(alpha = 0.45f),
                shape = RoundedCornerShape(size = 6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    }
    // The rule id is always the prominent, on-brand color — selection is shown by the row
    // background/border instead, so an unselected row's id no longer reads as plain body text.
    val idColor = PrimaryBlue
    val idWeight = FontWeight.SemiBold

    Row(modifier = rowModifier, verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.weight(weight = 0.22f),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            Text(
                text = rule.tableRuleId(),
                style = MaterialTheme.typography.body2.copy(fontWeight = idWeight),
                color = idColor,
            )
            // Rendered even when empty: an undescribed rule is a gap in the exported overview, and a
            // blank cell would read as "nothing to say here" rather than "still to write".
            rule.tableDescription()?.let { description ->
                Text(
                    text = description.ifBlank { "no description" },
                    style = MaterialTheme.typography.caption,
                    color = if (description.isBlank()) TextMuted else TextSecondary,
                )
            }
        }
        Box(modifier = Modifier.width(width = 80.dp)) {
            if (status != null) {
                StatusBadge(
                    label = status.status.label,
                    color = when (status.status) {
                        CatalogRuleStatus.VALID -> AccentGreen
                        CatalogRuleStatus.INVALID -> AccentRed
                        CatalogRuleStatus.DRAFT -> AccentOrange
                    },
                )
            }
        }
        Column(
            modifier = Modifier.weight(weight = 0.48f),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            when (rule) {
                is BuilderRule.Supported -> {
                    if (rule.conditionNodes.isEmpty()) {
                        Text(text = "—", style = MaterialTheme.typography.body2, color = TextMuted)
                    } else {
                        rule.conditionNodes.flatMap { it.toSummaryLines() }.forEach { line ->
                            Text(text = line, style = MaterialTheme.typography.body2, color = TextSecondary)
                        }
                    }
                }
                is BuilderRule.Unsupported -> Text(
                    text = "⚠ ${rule.reason}",
                    style = MaterialTheme.typography.body2,
                    color = AccentOrange,
                )
                BuilderRule.None -> Unit
            }
        }
        Column(
            modifier = Modifier.weight(weight = 0.30f),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            when (rule) {
                is BuilderRule.Supported -> {
                    if (rule.actions.isEmpty()) {
                        Text(text = "—", style = MaterialTheme.typography.body2, color = TextMuted)
                    } else {
                        rule.actions.forEach { action ->
                            Text(
                                text = action.toDisplaySummary(),
                                style = MaterialTheme.typography.body2,
                                color = TextSecondary,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun conditionValuePart(value: String, valueTo: String, listItems: List<String>): String = when {
    listItems.isNotEmpty() -> "[${listItems.joinToString(separator = ", ")}]"
    valueTo.isNotBlank() -> "$value … $valueTo"
    else -> value
}

private fun BuilderConditionNode.toSummaryLines(indent: String = ""): List<String> {
    val join = if (joinToPrevious.isNotBlank()) "${joinToPrevious.uppercase()} " else ""
    val not = if (negated) "NOT " else ""
    return when (this) {
        is BuilderCondition -> {
            val valuePart = conditionValuePart(value = value, valueTo = valueTo, listItems = listItems)
            listOf("$indent$join$not$field $operator $valuePart")
        }
        is BuilderConditionNode.Condition -> {
            val valuePart = conditionValuePart(value = value, valueTo = valueTo, listItems = listItems)
            listOf("$indent$join$not$field $operator $valuePart")
        }
        is BuilderConditionNode.Comparison -> {
            val left = OperandText.toLabel(operand = left)
            val right = OperandText.toLabel(operand = right)
            listOf("$indent$join$not$left $operator $right")
        }
        is BuilderConditionNode.Group -> {
            listOf("$indent$join$not(") +
                nodes.flatMap { it.toSummaryLines(indent = "$indent  ") } +
                listOf("$indent)")
        }
    }
}

private fun BuilderAction.toDisplaySummary(): String {
    val args = arguments.joinToString(separator = ", ")
    return if (args.isEmpty()) name else "$name($args)"
}

private fun BuilderRule.tableRuleId(): String = when (this) {
    is BuilderRule.Supported -> id
    is BuilderRule.Unsupported -> id
    BuilderRule.None -> ""
}

/**
 * Null for a rule the Builder could not map: it never read that rule's body, so reporting "no
 * description" would be a claim it cannot make — the clause may well be there in the file.
 */
private fun BuilderRule.tableDescription(): String? = when (this) {
    is BuilderRule.Supported -> description
    is BuilderRule.Unsupported -> null
    BuilderRule.None -> null
}
