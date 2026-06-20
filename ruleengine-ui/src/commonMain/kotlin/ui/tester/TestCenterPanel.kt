package ui.tester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentGreen
import ui.AccentRed
import ui.Bg
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.builder.components.DropdownSelector
import ui.components.PrimaryButton
import ui.components.SectionTitle

/**
 * Full center Test mode.
 *
 * @param state           Current [TestInputState].
 * @param onStateChange   Called on every user edit (selected rule or JSON).
 * @param onRunTest       Called when the user clicks [Run test].
 * @param ruleIds         Available rule ids to choose from.
 * @param runEnabled      Whether the run control is enabled.
 * @param runReason       Optional explanation shown when [runEnabled] is false.
 */
@Composable
fun TestCenterPanel(
    state: TestInputState,
    onStateChange: (TestInputState) -> Unit,
    onRunTest: () -> Unit,
    ruleIds: List<String>,
    runEnabled: Boolean = true,
    runReason: String? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(text = "TEST") }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Rule:",
                    style = MaterialTheme.typography.body2,
                    color = TextPrimary,
                )
                val selectorOptions = listOf("All rules") + ruleIds
                val displaySelection = state.selectedRuleId.ifBlank { "All rules" }
                DropdownSelector(
                    selected = displaySelection,
                    options = selectorOptions,
                    onSelected = { selected ->
                        val newId = if (selected == "All rules") "" else selected
                        onStateChange(state.copy(selectedRuleId = newId))
                    },
                )
            }
        }

        item {
            OutlinedTextField(
                value = state.inputJson,
                onValueChange = { onStateChange(state.copy(inputJson = it)) },
                label = { Text("Input JSON") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
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
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            }
        }

        item { ResultSection(state = state) }

        if (state.traceRows.isNotEmpty()) {
            item { SectionTitle(text = "TRACE") }
            item { TraceView(rows = state.traceRows) }
        }
    }
}

@Composable
private fun ResultSection(state: TestInputState) {
    when (val outcome = state.outcome) {
        is SimulationOutcome.Idle -> Unit
        is SimulationOutcome.Matched -> {
            ResultBlock(text = "✓ Matched", color = AccentGreen)
            if (outcome.actions.isNotEmpty()) {
                Text(
                    text = "Actions emitted:",
                    style = MaterialTheme.typography.caption,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                outcome.actions.forEach { action ->
                    Text(
                        text = action,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimary,
                        ),
                    )
                }
            }
        }

        is SimulationOutcome.NotMatched -> ResultBlock(text = "✕ Not matched", color = AccentRed)
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
