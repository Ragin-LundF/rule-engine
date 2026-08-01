package ui.tester

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentGreen
import ui.Bg
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.builder.components.dropdown.DropdownSelector
import ui.components.PrimaryButton
import ui.components.SecondaryButton
import ui.components.SectionTitle
import ui.tester.model.RuleResult
import ui.tester.model.SimulationOutcome
import ui.tester.model.TestInputState
import ui.tester.model.TestResultTab

/**
 * Full center Test mode.
 *
 * The run control and the one-line verdict are pinned below the scrolling area rather than being the
 * last items of it. The input box is 240 dp tall, which is more than the center panel has to spare once
 * the Diagnostics panel takes its share, so a trailing result would land below the fold and a run would
 * look like it did nothing at all.
 *
 * @param state           Current [TestInputState].
 * @param onStateChange   Called on every user edit (selected rule or JSON).
 * @param onRunTest       Called when the user clicks [Run test].
 * @param ruleIds         Available rule ids to choose from.
 * @param runEnabled      Whether the run control is enabled.
 * @param runReason       Optional explanation shown when [runEnabled] is false.
 * @param onLoadJson      Called when the user picks an input JSON file; null hides the load control.
 * @param traceContent    Draws the recorded decision trees. Supplied by the platform because the
 *   diagram renderer is JVM-side, the same way `CenterEditorPanel` takes its `testContent`. Null
 *   leaves the results as the only presentation and hides the tab strip.
 */
@Suppress("LongParameterList")
@Composable
fun TestCenterPanel(
    state: TestInputState,
    onStateChange: (TestInputState) -> Unit,
    onRunTest: () -> Unit,
    ruleIds: List<String>,
    runEnabled: Boolean = true,
    runReason: String? = null,
    ruleSelectionEnabled: Boolean = true,
    onLoadJson: (() -> Unit)? = null,
    traceContent: (@Composable (List<RuleResult>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        SectionTitle(text = "TEST")

        RuleSelectorRow(
            state = state,
            onStateChange = onStateChange,
            ruleIds = ruleIds,
            ruleSelectionEnabled = ruleSelectionEnabled,
            onLoadJson = onLoadJson,
        )

        // Input and result share the space the fixed rows leave, so the panel shows no dead space, the
        // result is never pushed out of sight, and a long result cannot squeeze the input to nothing.
        OutlinedTextField(
            value = state.inputJson,
            onValueChange = { onStateChange(state.copy(inputJson = it)) },
            label = { Text("Input JSON") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TextPrimary,
            ),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = Bg,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BorderColor,
                cursorColor = PrimaryBlue,
            ),
        )

        if (state.outcome !is SimulationOutcome.Idle) {
            ResultArea(
                state = state,
                traceContent = traceContent,
                modifier = Modifier.weight(weight = 1f),
            )
        }

        Divider(color = BorderColor, thickness = 1.dp)

        RunBar(
            state = state,
            runEnabled = runEnabled,
            runReason = runReason,
            onRunTest = onRunTest,
        )
    }
}

/**
 * Result and trace of the last run, scrolling within the share of the panel it is given. The caller
 * omits it entirely while idle so the input box gets the whole panel before the first run.
 *
 * The two presentations are tabs rather than one below the other: they answer the same question at
 * different depths, and stacking them would push the roster off the top on every run.
 */
@Composable
private fun ResultArea(
    state: TestInputState,
    traceContent: (@Composable (List<RuleResult>) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val outcome = state.outcome
    val completed = outcome as? SimulationOutcome.Completed
    var tab by remember { mutableStateOf(TestResultTab.RESULTS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(state = rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        // Only a completed run has trees to draw; a validation failure has one message and no tabs.
        if (completed != null && traceContent != null) {
            ResultTabs(current = tab, onSelect = { selected -> tab = selected })
        }
        if (completed != null && traceContent != null && tab == TestResultTab.TRACE) {
            traceContent(completed.ruleResults)
        } else {
            ResultSection(state = state)
        }
    }
}

@Composable
private fun ResultTabs(current: TestResultTab, onSelect: (TestResultTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TestResultTab.entries.forEach { entry ->
            val isSelected = entry == current
            Text(
                text = entry.label(),
                style = MaterialTheme.typography.caption,
                color = if (isSelected) PrimaryBlue else TextMuted,
                modifier = Modifier
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun TestResultTab.label(): String {
    return when (this) {
        TestResultTab.RESULTS -> "Results"
        TestResultTab.TRACE -> "Trace diagram"
    }
}

@Composable
private fun RuleSelectorRow(
    state: TestInputState,
    onStateChange: (TestInputState) -> Unit,
    ruleIds: List<String>,
    ruleSelectionEnabled: Boolean,
    onLoadJson: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Rule:",
            style = MaterialTheme.typography.body2,
            color = TextPrimary,
        )
        if (ruleSelectionEnabled) {
            val selectorOptions = listOf("All rules") + ruleIds
            val displaySelection = state.selectedRuleId.ifBlank { "All rules" }
            // Weighted, because the selector fills whatever width it is given and would otherwise
            // squeeze the load button down to nothing.
            DropdownSelector(
                selected = displaySelection,
                options = selectorOptions,
                onSelected = { selected ->
                    val newId = if (selected == "All rules") "" else selected
                    onStateChange(state.copy(selectedRuleId = newId))
                },
                modifier = Modifier.weight(weight = 1f),
            )
        } else {
            Text(
                text = "All rules",
                style = MaterialTheme.typography.body2,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.weight(weight = 1f))
        }
        // A real payload is far too large to paste into a text field, so it has to be loadable.
        onLoadJson?.let { load ->
            SecondaryButton(text = "Load JSON…", onClick = load)
        }
    }
}

/**
 * The pinned control row: the run button plus a single line saying what the last run decided, so an
 * outcome is never only reachable by scrolling.
 */
@Composable
private fun RunBar(
    state: TestInputState,
    runEnabled: Boolean,
    runReason: String?,
    onRunTest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryButton(
            text = if (state.isRunning) "Running…" else "Run test",
            enabled = runEnabled && !state.isRunning,
            onClick = onRunTest,
        )
        runReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
            )
        }
        VerdictText(outcome = state.outcome)
    }
}

/** One-line summary of the last run. Renders nothing while idle. */
@Composable
private fun VerdictText(outcome: SimulationOutcome) {
    val (text, color) = when (outcome) {
        is SimulationOutcome.Idle -> return
        is SimulationOutcome.Completed -> verdictSummary(outcome = outcome) to
            if (outcome.matchedCount > 0) AccentGreen else TextMuted
        is SimulationOutcome.ValidationFailed -> "Validation failed" to MaterialTheme.colors.error
        is SimulationOutcome.InvalidJson -> "Invalid JSON" to MaterialTheme.colors.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = color,
    )
}

private fun verdictSummary(outcome: SimulationOutcome.Completed): String {
    return "${outcome.matchedCount} of ${outcome.ruleResults.size} matched · ${outcome.actionCount} action(s)"
}

@Composable
private fun ResultSection(state: TestInputState) {
    when (val outcome = state.outcome) {
        is SimulationOutcome.Idle -> Unit
        is SimulationOutcome.Completed -> RuleResultsView(results = outcome.ruleResults)
        is SimulationOutcome.ValidationFailed -> ResultBlock(
            text = "Validation failed: ${outcome.reason}",
            color = MaterialTheme.colors.error,
        )

        is SimulationOutcome.InvalidJson -> ResultBlock(
            text = "Invalid JSON: ${outcome.reason}",
            color = MaterialTheme.colors.error,
        )
    }
}

@Composable
private fun ResultBlock(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.subtitle1,
        color = color,
    )
}
