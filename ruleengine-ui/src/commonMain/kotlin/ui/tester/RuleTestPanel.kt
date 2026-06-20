package ui.tester

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ui.components.PrimaryButton
import ui.components.SectionTitle

/**
 * Test panel composable — allows the user to paste sample JSON and run the selected rule.
 *
 * @param state         Current [TestInputState].
 * @param onJsonChange  Called when the user edits the JSON input.
 * @param onRunTest     Called when the user clicks [Run test].
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun RuleTestPanel(
    state: TestInputState,
    onJsonChange: (String) -> Unit,
    onRunTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Simulate")

        // ── JSON input ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = state.inputJson,
            onValueChange = onJsonChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            placeholder = {
                Text(
                    text = "{\n  \"purpose\": \"rent\",\n  \"amount\": 750\n}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextMuted,
                    ),
                )
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TextPrimary,
            ),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = Bg,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BorderColor,
                cursorColor = PrimaryBlue,
            ),
            shape = RoundedCornerShape(6.dp),
        )

        // ── Run button ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            PrimaryButton(
                text = if (state.isRunning) "Running…" else "Run test",
                enabled = !state.isRunning && state.inputJson.isNotBlank(),
                onClick = onRunTest,
            )
        }

        // ── Result ────────────────────────────────────────────────────────────
        when (val outcome = state.outcome) {
            is SimulationOutcome.Idle -> Unit

            is SimulationOutcome.Matched -> {
                OutcomeBlock(
                    icon = "✓",
                    label = "Matched",
                    color = AccentGreen,
                )
                if (outcome.actions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Actions:",
                        style = MaterialTheme.typography.caption,
                        color = TextMuted,
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

            is SimulationOutcome.NotMatched -> {
                OutcomeBlock(
                    icon = "✕",
                    label = "Not matched",
                    color = AccentRed,
                )
            }

            is SimulationOutcome.ValidationFailed -> {
                OutcomeBlock(
                    icon = "✕",
                    label = "Validation failed",
                    color = AccentRed,
                )
                Text(
                    text = outcome.reason,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AccentRed,
                    ),
                )
            }

            is SimulationOutcome.InvalidJson -> {
                OutcomeBlock(
                    icon = "✕",
                    label = "Invalid JSON",
                    color = AccentRed,
                )
                Text(
                    text = outcome.reason,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AccentRed,
                    ),
                )
            }
        }

        // ── Trace ─────────────────────────────────────────────────────────────
        if (state.traceRows.isNotEmpty()) {
            TraceView(rows = state.traceRows)
        }
    }
}

// ── private helpers ───────────────────────────────────────────────────────────

@Suppress("FunctionNaming")
@Composable
private fun OutcomeBlock(icon: String, label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = icon, color = color, fontSize = 16.sp)
        Text(
            text = label,
            style = MaterialTheme.typography.subtitle2,
            color = color,
        )
    }
}
