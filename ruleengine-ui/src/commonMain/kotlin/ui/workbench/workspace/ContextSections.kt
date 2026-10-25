package ui.workbench.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.ConditionVerdict
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgHover
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.model.catalog.CatalogFieldInfo
import ui.tester.model.RuleMatchStatus
import ui.tester.model.RuleResult
import ui.tester.model.SimulationOutcome
import ui.tester.model.TraceRow
import ui.workbench.model.workspace.ContextScope

/**
 * What the open rule did with the test record: which outcome lane ran, what it produced, and how each
 * condition answered.
 *
 * Read from the last run rather than run on every keystroke, because a run is the engine's own answer —
 * the same one the Test mode reports — and a second, quicker evaluator here would be a second answer.
 */
@Suppress("FunctionNaming")
@Composable
internal fun RuleOutcomeSection(ruleId: String, outcome: SimulationOutcome) {
    ContextTitle(text = if (ruleId.isBlank()) "Outcome" else "$ruleId gets")
    when (outcome) {
        SimulationOutcome.Idle -> ContextNote(text = "Run the record to see which outcome this rule produces.")
        is SimulationOutcome.InvalidJson -> ContextNote(text = outcome.reason, colour = AccentRed)
        is SimulationOutcome.ValidationFailed -> ContextNote(text = outcome.reason, colour = AccentRed)
        is SimulationOutcome.Completed -> {
            val result = outcome.ruleResults.firstOrNull { result -> result.ruleId == ruleId }
            if (result == null) {
                ContextNote(text = "The last run did not include this rule. Run again after selecting it.")
            } else {
                RuleResultView(result = result)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RuleResultView(result: RuleResult) {
    val (lane, colour) = laneOf(status = result.status)
    Text(
        text = lane,
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = colour,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = colour.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
    (result.assignments.map { line -> "set $line" } + result.actions).forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary,
        )
    }
    result.traceRows.forEach { row -> TraceLine(row = row) }
}

/** One condition's answer. `?` for a condition the record carried no data for — not a failure. */
@Suppress("FunctionNaming")
@Composable
private fun TraceLine(row: TraceRow) {
    val (glyph, colour) = when (row.verdict) {
        ConditionVerdict.TRUE -> "✓" to AccentGreen
        ConditionVerdict.FALSE -> "✗" to AccentRed
        ConditionVerdict.UNKNOWN -> "?" to AccentOrange
    }
    Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        Text(text = glyph, style = MaterialTheme.typography.caption, color = colour)
        Text(
            text = row.actual?.let { actual -> "${row.label} (= $actual)" } ?: row.label,
            style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
            color = TextSecondary,
        )
    }
}

/** The lane a status ran, in the words the outcome lanes use. */
private fun laneOf(status: RuleMatchStatus): Pair<String, Color> {
    return when (status) {
        RuleMatchStatus.MATCHED -> "When true" to AccentGreen
        RuleMatchStatus.ELSE_MATCHED -> "When false" to AccentRed
        RuleMatchStatus.NOT_EXISTS_MATCHED -> "When data is missing" to AccentOrange
        RuleMatchStatus.NOT_EVALUATED -> "Not reached — an earlier rule stopped the run" to TextSecondary
        RuleMatchStatus.PARTIAL, RuleMatchStatus.NO_MATCH -> "No outcome — the conditions did not hold" to
            TextSecondary
    }
}

/**
 * What can be named at the selection: the record's fields and the variables earlier rules publish, or —
 * inside a list — the element's members.
 */
@Suppress("FunctionNaming")
@Composable
internal fun ScopeSection(scope: ContextScope) {
    ContextTitle(text = scope.title)
    if (scope.fields.isEmpty() && scope.variables.isEmpty()) {
        ContextNote(text = "Nothing declared here. Load a schema to see what a rule can name.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        scope.fields.forEach { field -> ScopeLine(field = field) }
    }
    if (scope.variables.isNotEmpty()) {
        Text(
            text = "Published by earlier rules",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            scope.variables.forEach { field -> ScopeLine(field = field) }
        }
    }
    scope.note?.let { note -> ContextNote(text = note) }
}

@Suppress("FunctionNaming")
@Composable
private fun ScopeLine(field: CatalogFieldInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = typeGlyph(type = field.type),
            style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            softWrap = false,
            modifier = Modifier
                .width(width = GLYPH_WIDTH)
                .clip(shape = RoundedCornerShape(size = 4.dp))
                .background(color = BgHover),
        )
        Text(
            text = field.id,
            style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f),
        )
        Text(
            text = field.type,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A two-character type marker, so a column of names can be scanned by kind. */
private fun typeGlyph(type: String): String {
    return when (type.lowercase()) {
        "text", "string" -> "Aa"
        "integer" -> "1"
        "decimal", "number" -> "1.5"
        "boolean" -> "y/n"
        "date", "date_time" -> "D"
        "collection", "string_set" -> "[ ]"
        "object" -> "{ }"
        else -> "·"
    }
}

/** The keys the workspace answers to. Short, because there are few. */
@Suppress("FunctionNaming")
@Composable
internal fun KeysSection() {
    ContextTitle(text = "Keys")
    ContextNote(
        text = "Esc goes up one level. Click any part of a condition to edit it under the row; the bar " +
            "above the rule always shows where you are, and every part of it can be clicked.",
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun ContextNote(text: String, colour: Color = TextSecondary) {
    Text(text = text, style = MaterialTheme.typography.caption, color = colour)
}

private val GLYPH_WIDTH = 30.dp
