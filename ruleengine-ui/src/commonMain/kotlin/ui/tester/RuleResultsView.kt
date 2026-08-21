package ui.tester

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.Bg
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.components.dropdown.DropdownSelector
import ui.components.InfoChip
import ui.components.StatusBadge
import ui.tester.model.RuleMatchStatus
import ui.tester.model.RuleResult
import ui.tester.model.displayLabel
import ui.tester.model.rowKey

private const val FILTER_ALL = "All"
private const val FILTER_MATCHED = "Matched"
private const val FILTER_ELSE = "Else"
private const val FILTER_NOT_EXISTS = "Not exists"
private const val FILTER_PARTIAL = "Partial"
private const val FILTER_NO_MATCH = "No match"
private const val FILTER_NOT_EVALUATED = "Not evaluated"

/**
 * Must be a getter, not a `val`.
 *
 * [TextPrimary] reads the active palette on every access, so capturing it in a file-level `val`
 * freezes whichever theme happened to load first — which rendered the emitted actions in near-black
 * on the dark background.
 */
private val MonoText: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = TextPrimary,
    )

/**
 * What every rule in the run decided.
 *
 * A run over a whole manifest produces one answer per rule, and printing them all in full — verdict,
 * actions and trace, one after another — is more than anyone can read. So the panel answers the two
 * questions in order: what came out of the run (every emitted action, grouped by the rule that
 * emitted it), then which rules produced it (one compact row each, trace on demand).
 *
 * Rules that did not match are muted rather than red. In a rule set built from mutually exclusive
 * pairs roughly half must always stay silent, so red there reads as breakage where there is none —
 * red is left to mean an actual error.
 *
 * A plain [Column] rather than a `LazyColumn`, because both call sites already scroll and nesting
 * scrollable containers gives the inner one an unbounded height constraint.
 */
@Suppress("FunctionNaming")
@Composable
fun RuleResultsView(
    results: List<RuleResult>,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) return

    var filter by remember { mutableStateOf(FILTER_ALL) }
    var expandedRuleIds by remember { mutableStateOf(emptySet<String>()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        SummaryLine(results = results)
        VariablesSetBlock(results = results)
        ActionsEmittedBlock(results = results)
        RulesHeaderRow(filter = filter, onFilterChange = { selected -> filter = selected })

        results.filter { result -> matchesFilter(result = result, filter = filter) }
            .forEach { result ->
                RuleResultRow(
                    result = result,
                    expanded = result.rowKey in expandedRuleIds,
                    onToggle = { expandedRuleIds = toggle(ids = expandedRuleIds, id = result.rowKey) },
                )
            }
    }
}

// ── private helpers ───────────────────────────────────────────────────────────

private fun matchesFilter(result: RuleResult, filter: String): Boolean {
    return when (filter) {
        FILTER_MATCHED -> result.status == RuleMatchStatus.MATCHED
        FILTER_ELSE -> result.status == RuleMatchStatus.ELSE_MATCHED
        FILTER_NOT_EXISTS -> result.status == RuleMatchStatus.NOT_EXISTS_MATCHED
        FILTER_PARTIAL -> result.status == RuleMatchStatus.PARTIAL
        FILTER_NO_MATCH -> result.status == RuleMatchStatus.NO_MATCH
        FILTER_NOT_EVALUATED -> result.status == RuleMatchStatus.NOT_EVALUATED
        else -> true
    }
}

private fun toggle(ids: Set<String>, id: String): Set<String> {
    return if (id in ids) ids - id else ids + id
}

private fun statusColor(status: RuleMatchStatus): Color {
    return when (status) {
        RuleMatchStatus.MATCHED -> AccentGreen
        // Green like a match: the rule decided and produced output. It is a different decision, not a
        // worse one, which the label is what distinguishes.
        RuleMatchStatus.ELSE_MATCHED -> AccentGreen
        // Orange, not green: the rule produced output, but it did so without deciding — the record
        // carried no data to answer its condition, which is worth a second look.
        RuleMatchStatus.NOT_EXISTS_MATCHED -> AccentOrange
        RuleMatchStatus.PARTIAL -> AccentOrange
        RuleMatchStatus.NO_MATCH -> AccentRed
        // Muted, not red: the rule did not fail, it was never asked.
        RuleMatchStatus.NOT_EVALUATED -> TextMuted
    }
}

private fun statusLabel(status: RuleMatchStatus): String {
    return when (status) {
        RuleMatchStatus.MATCHED -> "matched"
        RuleMatchStatus.ELSE_MATCHED -> "else"
        RuleMatchStatus.NOT_EXISTS_MATCHED -> "not exists"
        RuleMatchStatus.PARTIAL -> "partial"
        RuleMatchStatus.NO_MATCH -> "no match"
        RuleMatchStatus.NOT_EVALUATED -> "not evaluated"
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SummaryLine(results: List<RuleResult>) {
    val byStatus = results.groupingBy { result -> result.status }.eachCount()
    val matched = byStatus[RuleMatchStatus.MATCHED] ?: 0
    val elseMatched = byStatus[RuleMatchStatus.ELSE_MATCHED] ?: 0
    val notExistsMatched = byStatus[RuleMatchStatus.NOT_EXISTS_MATCHED] ?: 0
    val partial = byStatus[RuleMatchStatus.PARTIAL] ?: 0
    val noMatch = byStatus[RuleMatchStatus.NO_MATCH] ?: 0
    val notEvaluated = byStatus[RuleMatchStatus.NOT_EVALUATED] ?: 0
    val actions = results.sumOf { result -> result.actions.size }

    // Coloured by the best outcome in the run, so the headline never reads worse than the roster.
    val color = when {
        matched > 0 || elseMatched > 0 -> AccentGreen
        partial > 0 -> AccentOrange
        else -> AccentRed
    }
    // Each optional count is shown only when the run produced one, so a rule set that uses neither
    // branches nor `stop` keeps the headline it had.
    val elsePart = if (elseMatched > 0) "$elseMatched else · " else ""
    val notExistsPart = if (notExistsMatched > 0) "$notExistsMatched not exists · " else ""
    val stoppedPart = if (notEvaluated > 0) " · $notEvaluated not evaluated" else ""
    Text(
        text = "$matched matched · $elsePart$notExistsPart$partial partial · " +
                "$noMatch no match$stoppedPart · $actions action(s)",
        style = MaterialTheme.typography.subtitle1,
        color = color,
    )
}

/**
 * Every variable the run published, grouped under the rule that set it.
 *
 * Sits above the actions because a variable is an input to the rules that follow, so reading the run
 * top to bottom should show it before the outcomes it helped decide.
 */
@Suppress("FunctionNaming")
@Composable
private fun VariablesSetBlock(results: List<RuleResult>) {
    val assigning = results.filter { result -> result.assignments.isNotEmpty() }
    if (assigning.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = Bg)
            .padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Text(
            text = "VARIABLES SET",
            style = MaterialTheme.typography.caption,
            color = TextMuted,
        )
        assigning.forEach { result ->
            Text(
                text = result.displayLabel,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            result.assignments.forEach { assignment ->
                Text(
                    text = assignment,
                    style = MonoText,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * Every action the run emitted, grouped under the rule that emitted it. This is the run's actual
 * output, so it comes before the rule roster instead of being buried per rule.
 */
@Suppress("FunctionNaming")
@Composable
private fun ActionsEmittedBlock(results: List<RuleResult>) {
    val emitting = results.filter { result -> result.actions.isNotEmpty() }
    if (emitting.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = Bg)
            .padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Text(
            text = "ACTIONS EMITTED",
            style = MaterialTheme.typography.caption,
            color = TextMuted,
        )
        emitting.forEach { result ->
            Text(
                text = result.displayLabel,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            result.actions.forEach { action ->
                Text(
                    text = action,
                    style = MonoText,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RulesHeaderRow(filter: String, onFilterChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RULES",
            style = MaterialTheme.typography.caption,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.weight(weight = 1f))
        DropdownSelector(
            selected = filter,
            options = listOf(
                FILTER_ALL, FILTER_MATCHED, FILTER_ELSE, FILTER_NOT_EXISTS, FILTER_PARTIAL,
                FILTER_NO_MATCH, FILTER_NOT_EVALUATED,
            ),
            onSelected = onFilterChange,
            modifier = Modifier.width(width = 160.dp),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RuleResultRow(
    result: RuleResult,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = statusColor(status = result.status)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(size = 6.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.caption,
                color = TextMuted,
            )
            Spacer(
                modifier = Modifier
                    .size(size = 7.dp)
                    .clip(shape = CircleShape)
                    .background(color = accent),
            )
            // The member is shown with the rule, not as a grouping header: a scoped run repeats
            // every rule per member, and the filter chips above reorder the rows freely.
            Text(
                text = result.displayLabel,
                style = MonoText,
                modifier = Modifier.weight(weight = 1f),
            )
            if (result.actions.isNotEmpty()) {
                InfoChip(label = "${result.actions.size} action(s)")
            }
            StatusBadge(
                label = statusLabel(status = result.status),
                color = accent,
            )
        }
        if (expanded) {
            RuleResultDetail(result = result)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RuleResultDetail(result: RuleResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, bottom = 4.dp),
    ) {
        if (result.traceRows.isEmpty()) {
            // Rare now that comparisons are instrumented, but still reachable — a rule can be
            // short-circuited before any condition is evaluated.
            Text(
                text = "No traced conditions for this rule.",
                style = MaterialTheme.typography.caption,
                color = TextMuted,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            TraceView(rows = result.traceRows)
        }
    }
}
