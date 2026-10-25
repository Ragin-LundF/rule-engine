package ui.workbench.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentCyan
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentPurple
import ui.AccentRed
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.board.ribbon.RibbonModel
import ui.builder.board.ribbon.VariableFlow
import ui.builder.board.ribbon.model.RibbonCard
import ui.builder.board.ribbon.model.RibbonGroup
import ui.builder.model.BuilderRule
import ui.components.TinyButton
import ui.components.rotateVertically
import ui.workbench.model.catalog.CatalogRuleStatus
import ui.workbench.model.catalog.RuleTreeFile

/**
 * The navigator: every rule of the entry in the order it runs, and what passes between them.
 *
 * It replaces the plain file-and-rule tree. A name alone said nothing about why a rule was where it
 * was; here each rule carries `↓ reads`, `↑ publishes` and `stops run`, which is the whole of what one
 * rule can do to another. The numbering runs across files because the run does not restart at a file,
 * and it is the number the context bar uses, so "Rule 3" means the same rule in both places.
 *
 * Built on [RibbonModel] and [VariableFlow] — the board's own derivation — so the two cannot give
 * different answers about the same rules. A read with no earlier publisher is shown in the warning
 * colour: the rule parses and runs, and silently never fires.
 *
 * Its width is a constant and never measured from its content: a long variable name truncates inside
 * its chip rather than widening the column into the workspace.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun RuleNavigator(
    files: List<RuleTreeFile>,
    rules: List<BuilderRule>,
    selectedRuleId: String?,
    onRuleSelected: (relativePath: String, ruleId: String) -> Unit,
    onAddRule: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) {
        CollapsedNavigator(onToggleExpanded = onToggleExpanded, modifier = modifier)
        return
    }

    val groups = remember(files, rules) { RibbonModel.groups(files = files, rules = rules) }
    val statuses = remember(files) {
        files.flatMap { file -> file.rules }.associate { rule -> rule.id to rule.status }
    }
    val collapsedFiles = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .width(width = WorkspaceLayout.NAVIGATOR_WIDTH)
            .fillMaxHeight()
            .testTag(tag = WorkspaceLayout.NAVIGATOR_TAG)
            .verticalScroll(state = rememberScrollState())
            .padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        NavigatorHeader(onAddRule = onAddRule, onToggleExpanded = onToggleExpanded)

        groups.forEach { group ->
            val open = collapsedFiles[group.relativePath] != true
            FileHeader(
                group = group,
                open = open,
                onToggle = { collapsedFiles[group.relativePath] = open },
            )
            if (open) {
                group.cards.forEach { card ->
                    RuleEntry(
                        card = card,
                        status = statuses[card.ruleId],
                        unresolved = VariableFlow.unresolvedReads(card = card, groups = groups),
                        selected = card.ruleId == selectedRuleId,
                        onClick = { onRuleSelected(group.relativePath, card.ruleId) },
                    )
                }
            }
        }

        Text(
            text = "↓ reads a variable · ↑ publishes one. A rule only sees what rules above it publish, " +
                "so this list is also the dependency order.",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun NavigatorHeader(onAddRule: () -> Unit, onToggleExpanded: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RUN ORDER",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = TextSecondary,
            modifier = Modifier.weight(weight = 1f),
        )
        TinyButton(text = "+ Rule", onClick = onAddRule, primary = true)
        TinyButton(text = "⟨", onClick = onToggleExpanded)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun FileHeader(group: RibbonGroup, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = if (open) "▾" else "▸",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        Text(
            text = group.relativePath.substringAfterLast(delimiter = '/'),
            style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One rule: its number in the run, its id, and what it reads, publishes and whether it stops the run. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
private fun RuleEntry(
    card: RibbonCard,
    status: CatalogRuleStatus?,
    unresolved: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(size = 8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = shape)
            .background(color = if (selected) PrimaryBlue.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) PrimaryBlue.copy(alpha = 0.45f) else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = "${card.ordinal}",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                softWrap = false,
                modifier = Modifier.width(width = 18.dp),
            )
            Text(
                text = card.ruleId,
                style = MaterialTheme.typography.body2.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) PrimaryBlue else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weight = 1f),
            )
            status?.let { known -> StatusDot(status = known) }
        }
        val chips = chipsOf(card = card, unresolved = unresolved)
        if (chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
                verticalArrangement = Arrangement.spacedBy(space = 3.dp),
            ) {
                chips.forEach { chip -> FlowChip(text = chip.first, colour = chip.second) }
            }
        }
    }
}

/** The chips for one rule: reads, then publishes, then the stop — the order a reader asks about them. */
private fun chipsOf(card: RibbonCard, unresolved: List<String>): List<Pair<String, Color>> {
    if (card.locked) {
        return listOf("code only" to TextSecondary)
    }
    val reads = card.reads.map { name ->
        if (name in unresolved) {
            "↓ \$$name · nothing earlier sets it" to AccentOrange
        } else {
            "↓ \$$name" to AccentCyan
        }
    }
    val sets = card.sets.map { name -> "↑ \$$name" to AccentPurple }
    val stop = if (card.halts) listOf("stops run" to AccentRed) else emptyList()
    return reads + sets + stop
}

@Suppress("FunctionNaming")
@Composable
private fun FlowChip(text: String, colour: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        color = colour,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = colour.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun StatusDot(status: CatalogRuleStatus) {
    val colour = when (status) {
        CatalogRuleStatus.VALID -> AccentGreen
        CatalogRuleStatus.INVALID -> AccentRed
        CatalogRuleStatus.DRAFT -> AccentOrange
    }
    Box(modifier = Modifier.size(size = 7.dp).clip(shape = CircleShape).background(color = colour))
}

/** The folded navigator: one strip, the whole of which opens it again. */
@Suppress("FunctionNaming")
@Composable
private fun CollapsedNavigator(onToggleExpanded: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(width = WorkspaceLayout.COLLAPSED_WIDTH)
            .fillMaxHeight()
            .testTag(tag = WorkspaceLayout.NAVIGATOR_TAG)
            .clickable(onClick = onToggleExpanded),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TinyButton(text = "⟩", onClick = onToggleExpanded, modifier = Modifier.padding(top = 6.dp))
        Text(
            text = "RUN ORDER",
            style = MaterialTheme.typography.caption.copy(letterSpacing = 1.5.sp),
            color = TextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 16.dp).rotateVertically(),
        )
    }
}
