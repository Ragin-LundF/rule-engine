package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ruleengine.compiler.Validator
import ruleengine.dsl.parser.Parser
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.copyToClipboard
import ui.editor.rules.AppButton
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.editor.rules.ViewMode
import ui.editor.rules.ViewModeToggle
import ui.pickRuleFile
import ui.saveDiagramAsPng
import ui.saveRuleToFile

/** Right panel header: title, Code/Diagram mode toggle, and context-sensitive action buttons. */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun RightPanelHeaderSection(
    state: RuleEditorState,
    scope: CoroutineScope,
    diagramGraphicsLayer: GraphicsLayer,
) {
    var viewMode by state.viewMode
    var ruleValue by state.ruleValue
    var showExpandedDiagram by state.showExpandedDiagram
    var diagnosticsList by state.diagnosticsList
    var diagnosticsText by state.diagnosticsText
    val parsedManifest by state.parsedManifest
    val selectedManifestEntry by state.selectedManifestEntry
    val selectedManifestRuleFile by state.selectedManifestRuleFile

    val currentEntryRuleFiles: List<String> = parsedManifest
        ?.entries
        ?.find { it.id == selectedManifestEntry }
        ?.rules
        .orEmpty()

    // ── Header: title + view-mode toggle + action buttons ─────
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Rule Editor", style = MaterialTheme.typography.h6, color = TextPrimary)
        Spacer(Modifier.width(width = 14.dp))
        // ── Code / Diagram tab strip ──────────────────────────
        ViewModeToggle(
            current = viewMode,
            onChange = { viewMode = it },
        )
        Spacer(modifier = Modifier.weight(1f))
        // Action buttons — only shown in Code mode
        if (viewMode == ViewMode.CODE) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Manifest rule-file picker ─────────────────────
                if (currentEntryRuleFiles.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        AppButton(label = "☰") { expanded = true }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .background(color = BgElevated)
                                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
                        ) {
                            currentEntryRuleFiles.forEach { relativePath ->
                                val fileName = relativePath.substringAfterLast('/')
                                val isSelected = relativePath == selectedManifestRuleFile
                                DropdownMenuItem(
                                    onClick = {
                                        state.loadSingleManifestRuleFile(relativePath)
                                        expanded = false
                                    },
                                    modifier = Modifier.background(
                                        color = if (isSelected) BgHover else BgElevated,
                                        shape = RoundedCornerShape(size = 6.dp),
                                    ),
                                ) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.body2,
                                        color = if (isSelected) PrimaryBlue else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
                AppButton(label = "Load Rule") {
                    scope.launch {
                        val c = pickRuleFile()
                        if (c != null) {
                            ruleValue = androidx.compose.ui.text.input.TextFieldValue(text = c)
                            state.setStatus(msg = "Rule loaded", kind = StatusKind.SUCCESS)
                        } else {
                            state.setStatus(msg = "Load cancelled", kind = StatusKind.IDLE)
                        }
                    }
                }
                AppButton(label = "Save Rule") {
                    if (ruleValue.text.isNotBlank()) {
                        if (!state.saveCurrentManifestRuleFile()) {
                            saveRuleToFile(filename = "rule.rule", content = ruleValue.text)
                            state.setStatus(msg = "Rule saved", kind = StatusKind.SUCCESS)
                        }
                    } else {
                        state.setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                    }
                }
                AppButton(label = "Copy Rule") {
                    if (ruleValue.text.isNotBlank()) {
                        copyToClipboard(ruleValue.text)
                        state.setStatus(msg = "Rule copied to clipboard", kind = StatusKind.SUCCESS)
                    } else {
                        state.setStatus(msg = "Nothing to copy", kind = StatusKind.IDLE)
                    }
                }
                AppButton(label = "Validate", primary = true) {
                    scope.launch {
                        runCatching {
                            if (state.parsedSchema.value == null) {
                                state.setStatus(
                                    msg = "No schema loaded",
                                    kind = StatusKind.ERROR
                                ); return@launch
                            }
                            if (ruleValue.text.isBlank()) {
                                state.setStatus(msg = "Rule is empty", kind = StatusKind.IDLE); return@launch
                            }
                            val asts = Parser(input = ruleValue.text).parseRules()
                            val result = Validator.validate(
                                asts = asts,
                                schema = state.parsedSchema.value!!,
                                actions = state.parsedActionSchema.value
                            )
                            if (result.isValid) {
                                state.setStatus(msg = "✓ Validation passed", kind = StatusKind.SUCCESS)
                                diagnosticsText = "No issues found"
                                diagnosticsList = emptyList()
                            } else {
                                state.setStatus(
                                    msg = "✗ ${result.diagnostics.size} issue(s) found",
                                    kind = StatusKind.ERROR
                                )
                                diagnosticsList = result.diagnostics
                                diagnosticsText = result.diagnostics.joinToString(
                                    separator = "\n"
                                ) { d ->
                                    "[${d.severity}] ${d.message}${
                                        d.suggestion?.let {
                                            " → $it"
                                        } ?: ""
                                    }"
                                }
                            }
                        }.onFailure { e ->
                            state.setStatus(msg = "Parse error: ${e.message}", kind = StatusKind.ERROR)
                            diagnosticsText = e.toString()
                            diagnosticsList = emptyList()
                        }
                    }
                }
            }
        } // end if CODE
        // ── Diagram-mode toolbar ──────────────────────────────────────────────
        if (viewMode == ViewMode.DIAGRAM) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppButton(label = "Export PNG") {
                    scope.launch {
                        runCatching {
                            val bitmap = diagramGraphicsLayer.toImageBitmap()
                            saveDiagramAsPng(bitmap = bitmap)
                            state.setStatus(msg = "Diagram exported as PNG", kind = StatusKind.SUCCESS)
                        }.onFailure {
                            state.setStatus(msg = "Export failed: ${it.message}", kind = StatusKind.ERROR)
                        }
                    }
                }
                AppButton(label = "⤢ Expand") {
                    showExpandedDiagram = true
                }
            }
        } // end if DIAGRAM
    }
}


