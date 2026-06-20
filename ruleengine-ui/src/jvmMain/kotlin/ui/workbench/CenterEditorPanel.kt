package ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ruleengine.compiler.Validator
import ruleengine.dsl.parser.Parser
import ui.BorderColor
import ui.BgSurface
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.BuilderEditorState
import ui.builder.CatalogActionInfo
import ui.builder.CatalogFieldInfo
import ui.builder.RuleBuilderView
import ui.copyToClipboard
import ui.editor.rules.AppButton
import ui.editor.rules.PanelDivider
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.editor.rules.ViewModeToggle
import ui.editor.rules.sections.MainEditorContentSection
import ui.saveDiagramAsPng
import ui.saveRuleToFile
import ui.pickRuleFile

/**
 * Center panel that dispatches to the correct mode view based on [ruleMode].
 */
@Suppress("FunctionNaming")
@Composable
fun CenterEditorPanel(
    state: RuleEditorState,
    scope: CoroutineScope,
    ruleMode: RuleMode,
    onRuleModeChange: (RuleMode) -> Unit,
    builderEditorState: BuilderEditorState = BuilderEditorState.fromBuilderRule(ui.builder.BuilderRule.None),
    catalogFields: List<CatalogFieldInfo> = emptyList(),
    catalogActions: List<CatalogActionInfo> = emptyList(),
    onBuilderDslChange: (String) -> Unit = {},
    onConditionSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewMode = ruleMode.toViewMode()
    val diagramGraphicsLayer = rememberGraphicsLayer()

    Column(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(all = 14.dp),
    ) {
        // Mode tabs are always visible so Builder/Test/Table are never dead-ends.
        CenterPanelHeader(
            state = state,
            scope = scope,
            viewMode = viewMode,
            onRuleModeChange = onRuleModeChange,
            diagramGraphicsLayer = diagramGraphicsLayer,
        )

        PanelDivider()

        Box(modifier = Modifier.weight(weight = 1f)) {
            when (viewMode) {
                ui.editor.rules.ViewMode.BUILDER -> RuleBuilderView(
                    editorState = builderEditorState,
                    catalogFields = catalogFields,
                    catalogActions = catalogActions,
                    onConditionSelected = onConditionSelected,
                    onDslChange = onBuilderDslChange,
                    modifier = Modifier.fillMaxSize(),
                )

                ui.editor.rules.ViewMode.CODE, ui.editor.rules.ViewMode.DIAGRAM -> Column(modifier = Modifier.fillMaxSize()) {
                    MainEditorContentSection(
                        state = state,
                        diagramGraphicsLayer = diagramGraphicsLayer,
                        isDiagram = viewMode == ui.editor.rules.ViewMode.DIAGRAM,
                    )
                }

                ui.editor.rules.ViewMode.TEST -> TestModePlaceholder(modifier = Modifier.fillMaxSize())
                ui.editor.rules.ViewMode.TABLE -> TableModePlaceholder(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Header that is shown above every center mode: title, mode tabs, and context actions. */
@Suppress("FunctionNaming")
@Composable
private fun CenterPanelHeader(
    state: RuleEditorState,
    scope: CoroutineScope,
    viewMode: ui.editor.rules.ViewMode,
    onRuleModeChange: (RuleMode) -> Unit,
    diagramGraphicsLayer: GraphicsLayer,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Rule Editor", style = MaterialTheme.typography.h6, color = TextPrimary)
        Spacer(Modifier.width(width = 14.dp))
        ViewModeToggle(
            current = viewMode,
            onChange = { onRuleModeChange(it.toRuleMode()) },
        )
        Spacer(modifier = Modifier.weight(1f))
        CenterPanelActions(
            state = state,
            scope = scope,
            viewMode = viewMode,
            diagramGraphicsLayer = diagramGraphicsLayer,
        )
    }
}

@Composable
private fun CenterPanelActions(
    state: RuleEditorState,
    scope: CoroutineScope,
    viewMode: ui.editor.rules.ViewMode,
    diagramGraphicsLayer: GraphicsLayer,
) {
    var ruleValue by state.ruleValue

    when (viewMode) {
        ui.editor.rules.ViewMode.CODE -> CodeModeActions(
            state = state,
            scope = scope,
            ruleValue = ruleValue,
            onRuleValueChange = { ruleValue = it },
        )

        ui.editor.rules.ViewMode.DIAGRAM -> DiagramModeActions(
            state = state,
            scope = scope,
            diagramGraphicsLayer = diagramGraphicsLayer,
        )

        ui.editor.rules.ViewMode.BUILDER, ui.editor.rules.ViewMode.TEST, ui.editor.rules.ViewMode.TABLE -> {
            // No global actions for these modes yet.
        }
    }
}

@Composable
private fun CodeModeActions(
    state: RuleEditorState,
    scope: CoroutineScope,
    ruleValue: TextFieldValue,
    onRuleValueChange: (TextFieldValue) -> Unit,
) {
    var diagnosticsList by state.diagnosticsList
    var diagnosticsText by state.diagnosticsText

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton(label = "Load Rule") {
            scope.launch {
                val c = pickRuleFile()
                if (c != null) {
                    onRuleValueChange(TextFieldValue(text = c))
                    state.setStatus(msg = "Rule loaded", kind = StatusKind.SUCCESS)
                } else {
                    state.setStatus(msg = "Load cancelled", kind = StatusKind.IDLE)
                }
            }
        }
        AppButton(label = "Save Rule") {
            if (ruleValue.text.isNotBlank()) {
                saveRuleToFile(filename = "rule.rule", content = ruleValue.text)
                state.setStatus(msg = "Rule saved", kind = StatusKind.SUCCESS)
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
                        state.setStatus(msg = "No schema loaded", kind = StatusKind.ERROR)
                        return@launch
                    }
                    if (ruleValue.text.isBlank()) {
                        state.setStatus(msg = "Rule is empty", kind = StatusKind.IDLE)
                        return@launch
                    }
                    val asts = Parser(input = ruleValue.text).parseRules()
                    val result = Validator.validate(
                        asts = asts,
                        schema = state.parsedSchema.value!!,
                        actions = state.parsedActionSchema.value,
                    )
                    if (result.isValid) {
                        state.setStatus(msg = "✓ Validation passed", kind = StatusKind.SUCCESS)
                        diagnosticsText = "No issues found"
                        diagnosticsList = emptyList()
                    } else {
                        state.setStatus(
                            msg = "✗ ${result.diagnostics.size} issue(s) found",
                            kind = StatusKind.ERROR,
                        )
                        diagnosticsList = result.diagnostics
                        diagnosticsText = result.diagnostics.joinToString(separator = "\n") { d ->
                            "[${d.severity}] ${d.message}${d.suggestion?.let { " → $it" } ?: ""}"
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
}

@Composable
private fun DiagramModeActions(
    state: RuleEditorState,
    scope: CoroutineScope,
    diagramGraphicsLayer: GraphicsLayer,
) {
    var showExpandedDiagram by state.showExpandedDiagram

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
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
}

@Composable
private fun TestModePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Test mode will run the selected rule against sample JSON and show trace results.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}

@Composable
private fun TableModePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Table mode will list all rules with status, conditions, and actions.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}
