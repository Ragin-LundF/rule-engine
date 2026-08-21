package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgElevated
import ui.BorderColor
import ui.TextPrimary
import ui.TextSecondary
import ui.components.StatusBadge
import ui.diagnostics.DiagnosticMapper
import ui.diagnostics.DiagnosticsPanel
import ui.diagnostics.QuickFixes
import ui.diagnostics.model.QuickFix
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.RuleValidationOutcome
import ui.editor.rules.validateOpenEntry
import ui.util.Plurals

/** Diagnostics section: displays validation errors and warnings below the editor panels. */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun DiagnosticsSection(state: RuleEditorState) {
    val diagnosticsList by state.diagnosticsList
    val diagnosticsText by state.diagnosticsText
    var ruleValue by state.ruleValue
    var expanded by state.diagnosticsExpanded

    // Map raw ValidationDiagnostic list to enriched UiDiagnosticWithFix list. A diagnostic about another
    // file of the entry carries that file, so the row can say which one and the click can go there.
    val openFile = state.selectedManifestRuleFile.value
    val enriched = remember(diagnosticsList, openFile) {
        diagnosticsList.map { d ->
            DiagnosticMapper.map(
                severity = d.severity,
                message = d.message,
                suggestion = d.suggestion,
                line = d.line,
                column = d.column,
                file = d.file?.toString()?.takeIf { path -> path != openFile },
            )
        }
    }

    Spacer(modifier = Modifier.height(height = 12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 12.dp))
            .background(color = BgElevated)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 12.dp),
            )
            .padding(all = 14.dp),
    ) {
        DiagnosticsHeader(
            expanded = expanded,
            diagnostics = diagnosticsList,
            hasValidationText = diagnosticsText.isNotBlank(),
            onToggle = { expanded = !expanded },
        )

        if (!expanded) {
            return@Column
        }

        Spacer(modifier = Modifier.height(height = 10.dp))

        // Diagnostics panel
        DiagnosticsPanel(
            diagnostics = enriched,
            emptyText = diagnosticsText.ifBlank {
                "No diagnostics — press Validate to check your rule."
            }.let { text ->
                // Show green "No issues found" text when validation passed
                if (diagnosticsText.isNotBlank()) diagnosticsText else text
            },
            onRowClick = { d ->
                // A row for another file of the entry sends the editor there first; its line number is
                // relative to that file, so jumping without switching would land somewhere arbitrary.
                d.file?.let { path ->
                    state.loadSingleManifestRuleFile(relativePath = path)
                    // Switching a file clears the panel, which is right for an ordinary switch and wrong
                    // here: the entry-wide result still holds, and emptying the list would strand a
                    // reader half way through it. Re-running reproduces it against the new open file.
                    val outcome = state.validateOpenEntry()
                    if (outcome is RuleValidationOutcome.Completed) {
                        state.diagnosticsList.value = outcome.diagnostics
                    }
                }
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

/**
 * The collapsible header: a disclosure arrow, the title, and a count badge per severity.
 *
 * The whole row toggles rather than just the arrow, so the panel can hand its height back to the
 * centre panel with one click anywhere along it.
 */
@Suppress("FunctionNaming")
@Composable
private fun DiagnosticsHeader(
    expanded: Boolean,
    diagnostics: List<ValidationDiagnostic>,
    hasValidationText: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = if (expanded) "▼" else "▶",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.subtitle1,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.weight(weight = 1f))

        val errors = diagnostics.count { it.severity == Severity.ERROR }
        val warnings = diagnostics.count { it.severity == Severity.WARNING }
        if (errors > 0) {
            StatusBadge(label = "$errors error${Plurals.suffix(count = errors)}", color = AccentRed)
        }
        if (warnings > 0) {
            StatusBadge(label = "$warnings warning${Plurals.suffix(count = warnings)}", color = AccentOrange)
        }
        // Only after a validation has actually run: an untouched editor has no diagnostics either.
        if (diagnostics.isEmpty() && hasValidationText) {
            StatusBadge(label = "No issues", color = AccentGreen)
        }
    }
}
