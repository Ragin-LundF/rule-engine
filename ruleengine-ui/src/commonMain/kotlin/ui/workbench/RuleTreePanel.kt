package ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.components.StatusBadge
import ui.components.TinyButton
import ui.components.rotateVertically
import ui.workbench.model.CatalogRule
import ui.workbench.model.CatalogRuleStatus
import ui.workbench.model.RuleTreeFile

/**
 * File-and-rule tree shown in the left column of Builder mode.
 *
 * Files collapse/expand independently; clicking a rule row notifies the caller with both the
 * file it belongs to and its rule id, since selecting a rule in a file other than the one
 * currently open first requires loading that file.
 *
 * The whole panel collapses to a narrow strip (mirroring the right inspector panel), so the
 * rule editor gets the width while working on one rule; only one switching icon is ever
 * visible — `⟨` in the header when expanded, `⟩` on the strip when collapsed.
 */
@Suppress("FunctionNaming")
@Composable
fun RuleTreePanel(
    files: List<RuleTreeFile>,
    selectedRuleId: String?,
    onRuleSelected: (relativePath: String, ruleId: String) -> Unit,
    onAddRule: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
) {
    if (!expanded) {
        CollapsedRuleTreeStrip(onToggleExpanded = onToggleExpanded, modifier = modifier)
        return
    }

    val expandedByFile = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .width(width = 240.dp)
            .fillMaxHeight()
            .verticalScroll(state = rememberScrollState())
            .padding(end = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "RULES",
                style = MaterialTheme.typography.subtitle1,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                TinyButton(text = "+", onClick = onAddRule, primary = true)
                TinyButton(text = "⟨", onClick = onToggleExpanded)
            }
        }

        files.forEach { file ->
            val expanded = expandedByFile[file.relativePath] ?: true
            RuleTreeFileRow(
                file = file,
                expanded = expanded,
                onToggle = { expandedByFile[file.relativePath] = !expanded },
            )
            if (expanded) {
                file.rules.forEach { rule ->
                    RuleTreeRuleRow(
                        rule = rule,
                        selected = rule.id == selectedRuleId,
                        onClick = { onRuleSelected(file.relativePath, rule.id) },
                    )
                }
            }
        }
    }
}

/** Collapsed rule-tree strip: the whole column is clickable and re-expands the tree. */
@Suppress("FunctionNaming")
@Composable
private fun CollapsedRuleTreeStrip(
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(width = 36.dp)
            .fillMaxHeight()
            .clickable(onClick = onToggleExpanded),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TinyButton(
            text = "⟩",
            onClick = onToggleExpanded,
            modifier = Modifier.padding(top = 6.dp).size(size = 28.dp),
        )
        Text(
            text = "RULES",
            style = MaterialTheme.typography.caption.copy(letterSpacing = 1.5.sp),
            color = TextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .padding(top = 16.dp)
                .rotateVertically(),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RuleTreeFileRow(
    file: RuleTreeFile,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        Text(
            text = file.relativePath.substringAfterLast(delimiter = '/'),
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
            modifier = Modifier.weight(weight = 1f),
        )
        StatusBadge(label = file.rules.size.toString(), color = TextSecondary)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RuleTreeRuleRow(
    rule: CatalogRule,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) PrimaryBlue.copy(alpha = 0.12f) else Color.Transparent
    val border = if (selected) PrimaryBlue.copy(alpha = 0.45f) else Color.Transparent
    val idColor = if (selected) PrimaryBlue else TextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = bg)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(size = 6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        StatusDot(status = rule.status)
        Text(
            text = rule.id,
            style = MaterialTheme.typography.body2.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = idColor,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StatusDot(status: CatalogRuleStatus) {
    val color = when (status) {
        CatalogRuleStatus.VALID -> AccentGreen
        CatalogRuleStatus.INVALID -> AccentRed
        CatalogRuleStatus.DRAFT -> AccentOrange
    }
    Box(
        modifier = Modifier
            .size(size = 7.dp)
            .clip(shape = CircleShape)
            .background(color = color),
    )
}
