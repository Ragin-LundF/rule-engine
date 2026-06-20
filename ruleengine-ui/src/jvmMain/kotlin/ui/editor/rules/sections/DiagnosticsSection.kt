package ui.editor.rules.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ruleengine.core.errors.Severity
import ui.AccentOrange
import ui.AccentRed
import ui.TextPrimary
import ui.diagnostics.DiagnosticMapper
import ui.diagnostics.DiagnosticsPanel
import ui.diagnostics.QuickFix
import ui.diagnostics.QuickFixes
import ui.editor.rules.Chip
import ui.editor.rules.RuleEditorState

/** Diagnostics section: displays validation errors and warnings below the editor panels. */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun DiagnosticsSection(state: RuleEditorState) {
    val diagnosticsList by state.diagnosticsList
    val diagnosticsText by state.diagnosticsText
    var ruleValue by state.ruleValue

    // Map raw ValidationDiagnostic list to enriched UiDiagnosticWithFix list
    val enriched = remember(diagnosticsList) {
        diagnosticsList.map { d ->
            DiagnosticMapper.map(
                severity = when (d.severity) {
                    Severity.ERROR -> "error"
                    Severity.WARNING -> "warning"
                    else -> "info"
                },
                message = d.message,
                suggestion = d.suggestion,
                line = d.line,
                column = d.column,
            )
        }
    }

    Spacer(Modifier.height(10.dp))

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {

        // ── Header row ────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.h6, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            if (diagnosticsList.isNotEmpty()) {
                val errors = diagnosticsList.count { it.severity == Severity.ERROR }
                val warnings = diagnosticsList.count { it.severity == Severity.WARNING }
                if (errors > 0) {
                    Chip(
                        label = "$errors error${if (errors > 1) "s" else ""}",
                        bg = AccentRed.copy(alpha = 0.15f),
                        textColor = AccentRed,
                    )
                }
                if (warnings > 0) {
                    Chip(
                        label = "$warnings warning${if (warnings > 1) "s" else ""}",
                        bg = AccentOrange.copy(alpha = 0.15f),
                        textColor = AccentOrange,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        // ── Diagnostics panel ─────────────────────────────────────
        DiagnosticsPanel(
            diagnostics = enriched,
            emptyText = diagnosticsText.ifBlank {
                "No diagnostics — press Validate to check your rule."
            }.let { text ->
                // Show green "No issues found" text when validation passed
                if (diagnosticsText.isNotBlank()) diagnosticsText else text
            },
            onRowClick = { d ->
                // Jump cursor to the diagnostic location in the editor
                runCatching {
                    val line = d.line ?: return@runCatching
                    val col = d.column ?: 1
                    if (line > 0) {
                        val lines = ruleValue.text.lines()
                        var offset = 0
                        for (i in 0 until minOf(line - 1, lines.size - 1)) {
                            offset += lines[i].length + 1
                        }
                        if (col > 0) offset += (col - 1)
                        ruleValue = TextFieldValue(
                            ruleValue.text,
                            selection = TextRange(offset.coerceIn(0, ruleValue.text.length)),
                        )
                    }
                }
            },
            onApplyFix = { fix ->
                // Apply the quick-fix text substitution and write back to the editor
                if (fix !is QuickFix.None) {
                    val updated = QuickFixes.apply(fix = fix, ruleText = ruleValue.text)
                    ruleValue = TextFieldValue(text = updated)
                }
            },
        )
    }
}
