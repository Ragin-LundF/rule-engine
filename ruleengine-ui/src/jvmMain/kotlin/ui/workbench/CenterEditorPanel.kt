package ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.builder.model.BuilderRule
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.view.RuleBuilderView
import ui.components.SecondaryButton
import ui.components.ToolbarButton
import ui.copyToClipboard
import ui.diagrams.model.DiagramViewKind
import ui.editor.rules.RuleEditorState
import ui.editor.rules.RuleValidationRunner
import ui.editor.rules.ViewModeToggle
import ui.editor.rules.model.RuleValidationOutcome
import ui.editor.rules.model.StatusKind
import ui.editor.rules.model.ViewMode
import ui.editor.rules.sections.MainEditorContentSection
import ui.pickRuleFile
import ui.saveDiagramAsPng
import ui.workbench.export.ExportOverviewButton
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.catalog.RuleTreeFile
import ui.workbench.model.mode.RuleMode
import ui.workbench.rules.RuleTablePanel
import ui.workbench.rules.toRuleMode
import ui.workbench.rules.toViewMode

@Suppress("FunctionNaming")
@Composable
private fun ManifestFilePicker(state: RuleEditorState, viewMode: ViewMode) {
    val parsedManifest by state.parsedManifest
    val selectedManifestEntry by state.selectedManifestEntry
    val selectedManifestRuleFile by state.selectedManifestRuleFile
    val showAllRules by state.showAllRules

    val currentEntryRuleFiles: List<String> = parsedManifest
        ?.entries
        ?.find { it.id == selectedManifestEntry }
        ?.rules
        .orEmpty()

    // "All files" is only meaningful where the view can show more than one at once, and only when
    // the entry actually has more than one.
    val showAllFilesOption = currentEntryRuleFiles.size >= 2 &&
        (viewMode == ViewMode.DIAGRAM || viewMode == ViewMode.TEST)

    if (currentEntryRuleFiles.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarButton(label = "☰", onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(color = BgElevated)
                .border(
                    width = 1.dp,
                    color = BorderColor,
                    shape = RoundedCornerShape(size = 8.dp),
                ),
        ) {
            if (showAllFilesOption) {
                RuleFileMenuItem(label = "All files", selected = showAllRules) {
                    state.loadAllRuleFilesForCurrentEntry()
                    expanded = false
                }
                Divider(color = BorderColor, thickness = 1.dp)
            }
            currentEntryRuleFiles.forEach { relativePath ->
                RuleFileMenuItem(
                    label = relativePath.substringAfterLast('/'),
                    selected = !showAllRules && relativePath == selectedManifestRuleFile,
                ) {
                    state.loadSingleManifestRuleFile(relativePath)
                    expanded = false
                }
            }
        }
    }
}

/** One entry in the rule-file menu; "All files" and a single file look and behave the same. */
@Suppress("FunctionNaming")
@Composable
private fun RuleFileMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        onClick = onClick,
        modifier = Modifier.background(
            color = if (selected) BgHover else BgElevated,
            shape = RoundedCornerShape(size = 6.dp),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = if (selected) PrimaryBlue else TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Center panel that dispatches to the correct mode view based on [ruleMode]. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun CenterEditorPanel(
    state: RuleEditorState,
    scope: CoroutineScope,
    ruleMode: RuleMode,
    onRuleModeChange: (RuleMode) -> Unit,
    builderEditorState: BuilderEditorState = BuilderEditorState.fromBuilderRule(BuilderRule.None),
    allRuleIds: List<String> = emptyList(),
    allBuilderRules: List<BuilderRule> = emptyList(),
    catalogRules: List<CatalogRule> = emptyList(),
    onRuleSelected: (String) -> Unit = {},
    onAddRule: () -> Unit = {},
    onRenameRule: (oldId: String, newId: String) -> Unit = { _, _ -> },
    catalogFields: List<CatalogFieldInfo> = emptyList(),
    catalogActions: List<CatalogActionInfo> = emptyList(),
    onBuilderDslChange: (String) -> Unit = {},
    onConditionSelected: (String) -> Unit = {},
    testContent: @Composable () -> Unit = {},
    ruleTreeFiles: List<RuleTreeFile> = emptyList(),
    onTreeRuleSelected: (relativePath: String, ruleId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val viewMode = ruleMode.toViewMode()
    val diagramGraphicsLayer = rememberGraphicsLayer()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgElevated)
            .padding(all = 16.dp),
    ) {
        CenterPanelHeader(
            state = state,
            scope = scope,
            viewMode = viewMode,
            onRuleModeChange = onRuleModeChange,
            diagramGraphicsLayer = diagramGraphicsLayer,
        )

        Spacer(modifier = Modifier.height(height = 12.dp))
        Divider(color = BorderColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(height = 12.dp))

        Box(modifier = Modifier.weight(weight = 1f)) {
            when (viewMode) {
                ViewMode.BUILDER -> BuilderModeContent(
                    state = state,
                    builderEditorState = builderEditorState,
                    allRuleIds = allRuleIds,
                    catalogFields = catalogFields,
                    catalogActions = catalogActions,
                    ruleTreeFiles = ruleTreeFiles,
                    onRuleSelected = onRuleSelected,
                    onAddRule = onAddRule,
                    onRenameRule = onRenameRule,
                    onConditionSelected = onConditionSelected,
                    onBuilderDslChange = onBuilderDslChange,
                    onTreeRuleSelected = onTreeRuleSelected,
                )

                ViewMode.CODE,
                ViewMode.DIAGRAM,
                -> Column(modifier = Modifier.fillMaxSize()) {
                    MainEditorContentSection(
                        state = state,
                        diagramGraphicsLayer = diagramGraphicsLayer,
                        isDiagram = viewMode == ViewMode.DIAGRAM,
                    )
                }

                ViewMode.TEST -> testContent()
                ViewMode.TABLE -> RuleTablePanel(
                    allBuilderRules = allBuilderRules,
                    catalogRules = catalogRules,
                    selectedRuleId = builderEditorState.ruleId,
                    onRuleClick = { ruleId ->
                        onRuleSelected(ruleId)
                        onRuleModeChange(RuleMode.BUILDER)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Builder mode: the rule tree on the left, the selected rule's blocks on the right. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun BuilderModeContent(
    state: RuleEditorState,
    builderEditorState: BuilderEditorState,
    allRuleIds: List<String>,
    catalogFields: List<CatalogFieldInfo>,
    catalogActions: List<CatalogActionInfo>,
    ruleTreeFiles: List<RuleTreeFile>,
    onRuleSelected: (String) -> Unit,
    onAddRule: () -> Unit,
    onRenameRule: (oldId: String, newId: String) -> Unit,
    onConditionSelected: (String) -> Unit,
    onBuilderDslChange: (String) -> Unit,
    onTreeRuleSelected: (relativePath: String, ruleId: String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        RuleTreePanel(
            files = ruleTreeFiles,
            selectedRuleId = builderEditorState.ruleId,
            onRuleSelected = onTreeRuleSelected,
            onAddRule = onAddRule,
            expanded = state.ruleTreeExpanded.value,
            onToggleExpanded = { state.ruleTreeExpanded.value = !state.ruleTreeExpanded.value },
        )
        Divider(
            color = BorderColor,
            modifier = Modifier.width(width = 1.dp).fillMaxHeight(),
        )
        RuleBuilderView(
            editorState = builderEditorState,
            allRuleIds = allRuleIds,
            onRuleSelected = onRuleSelected,
            onAddRule = onAddRule,
            onRenameRule = onRenameRule,
            catalogFields = catalogFields,
            catalogActions = catalogActions,
            onConditionSelected = onConditionSelected,
            onDslChange = onBuilderDslChange,
            modifier = Modifier.weight(weight = 1f).fillMaxSize(),
        )
    }
}

/**
 * Header that is shown above every center mode: title, mode tabs, and context actions.
 *
 * The actions sit on their own row under the tabs rather than beside them. Sharing one row makes
 * the two compete for width — the tabs are fixed, so the actions absorb every shortfall, and the
 * last button gets squeezed until its label wraps to one letter per line. Which actions there are
 * depends on the mode, so that shortfall is not a fixed amount that could simply be designed around.
 */
@Suppress("FunctionNaming")
@Composable
private fun CenterPanelHeader(
    state: RuleEditorState,
    scope: CoroutineScope,
    viewMode: ViewMode,
    onRuleModeChange: (RuleMode) -> Unit,
    diagramGraphicsLayer: GraphicsLayer,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Rule Editor",
                style = MaterialTheme.typography.subtitle1,
                color = TextPrimary,
            )
            Spacer(Modifier.width(width = 14.dp))
            ViewModeToggle(
                current = viewMode,
                onChange = { onRuleModeChange(it.toRuleMode()) },
            )
        }
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
    viewMode: ViewMode,
    diagramGraphicsLayer: GraphicsLayer,
) {
    var ruleValue by state.ruleValue

    // Scrollable because the number of actions depends on the mode and the window can be narrower
    // than they need. Without it the row squeezes its last button instead, which is how "Validate"
    // once ended up rendered as a column of letters.
    Row(
        modifier = Modifier.horizontalScroll(state = rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManifestFilePicker(state = state, viewMode = viewMode)
        when (viewMode) {
            ViewMode.CODE -> CodeModeActions(
                state = state,
                scope = scope,
                ruleValue = ruleValue,
                onRuleValueChange = { ruleValue = it },
            )

            ViewMode.DIAGRAM -> DiagramModeActions(
                state = state,
                scope = scope,
                diagramGraphicsLayer = diagramGraphicsLayer,
            )

            ViewMode.BUILDER, ViewMode.TEST, ViewMode.TABLE -> {}
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(
            label = "Load Rule",
            onClick = {
                scope.launch {
                    val c = pickRuleFile()
                    if (c != null) {
                        onRuleValueChange(TextFieldValue(text = c))
                        state.setStatus(msg = "Rule loaded", kind = StatusKind.SUCCESS)
                    } else {
                        state.setStatus(msg = "Load cancelled", kind = StatusKind.IDLE)
                    }
                }
            },
        )
        // No "Save Rule" here: rule files are written by Save Project along with the manifest that
        // indexes them. A separate write also went behind the project's back, leaving it convinced
        // the file had been changed by someone else the next time it saved.
        ToolbarButton(
            label = "Copy Rule",
            onClick = {
                if (ruleValue.text.isNotBlank()) {
                    copyToClipboard(ruleValue.text)
                    state.setStatus(msg = "Rule copied to clipboard", kind = StatusKind.SUCCESS)
                } else {
                    state.setStatus(msg = "Nothing to copy", kind = StatusKind.IDLE)
                }
            },
        )
        ExportOverviewButton(state = state, scope = scope)
        ToolbarButton(
            label = "Validate",
            primary = true,
            onClick = { scope.launch { state.validateNow(ruleText = ruleValue.text) } },
        )
    }
}

/**
 * The Validate button's work: parse, validate, and report.
 *
 * Unlike the debounced pass in the editor, an explicit click reports a parse failure — someone who
 * pressed Validate is asking, so silence would read as "it is fine".
 */
private fun RuleEditorState.validateNow(ruleText: String) {
    val schema = parsedSchema.value
    if (schema == null) {
        setStatus(msg = "No schema loaded", kind = StatusKind.ERROR)
        return
    }
    if (ruleText.isBlank()) {
        setStatus(msg = "Rule is empty", kind = StatusKind.IDLE)
        return
    }

    when (
        val outcome = RuleValidationRunner.run(
            ruleText = ruleText,
            schema = schema,
            actions = parsedActionSchema.value,
        )
    ) {
        is RuleValidationOutcome.Completed -> if (outcome.isValid) {
            setStatus(msg = "Validation passed", kind = StatusKind.SUCCESS)
            diagnosticsText.value = "No issues found"
            diagnosticsList.value = emptyList()
        } else {
            setStatus(msg = "${outcome.diagnostics.size} issue(s) found", kind = StatusKind.ERROR)
            diagnosticsList.value = outcome.diagnostics
            diagnosticsText.value = outcome.diagnostics.joinToString(separator = "\n") { d ->
                "[${d.severity}] ${d.message}${d.suggestion?.let { " → $it" } ?: ""}"
            }
        }

        is RuleValidationOutcome.Threw -> {
            setStatus(msg = "Parse error: ${outcome.cause.message}", kind = StatusKind.ERROR)
            diagnosticsText.value = outcome.cause.toString()
            diagnosticsList.value = emptyList()
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
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiagramViewPicker(state = state)
        ToolbarButton(
            label = "Export PNG",
            onClick = {
                scope.launch {
                    runCatching {
                        val bitmap = diagramGraphicsLayer.toImageBitmap()
                        saveDiagramAsPng(bitmap = bitmap)
                        state.setStatus(msg = "Diagram exported as PNG", kind = StatusKind.SUCCESS)
                    }.onFailure {
                        state.setStatus(msg = "Export failed: ${it.message}", kind = StatusKind.ERROR)
                    }
                }
            },
        )
        SecondaryButton(
            text = "Expand",
            onClick = { showExpandedDiagram = true },
        )
    }
}

/**
 * Picks which diagram is drawn.
 *
 * Choosing the run or field view also switches the scope to the whole entry. An entry drawn from a
 * single open file would be a lie about what the engine runs, and "no rule reads this field" is a
 * claim about the entry — from one file it would report every field the other files read as dead.
 * Both drive the existing `showAllRules` mechanism rather than a scope selector of their own, so this
 * and the `☰` file picker can never end up disagreeing about what is on screen.
 */
@Suppress("FunctionNaming")
@Composable
private fun DiagramViewPicker(state: RuleEditorState) {
    var expanded by remember { mutableStateOf(false) }
    val current by state.diagramView

    Box {
        ToolbarButton(label = "▤ ${current.label()}", onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(color = BgElevated)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
        ) {
            DiagramViewKind.entries.forEach { kind ->
                val isSelected = kind == current
                DropdownMenuItem(
                    onClick = {
                        state.diagramView.value = kind
                        if (kind == DiagramViewKind.RUN || kind == DiagramViewKind.FIELDS) {
                            state.loadAllRuleFilesForCurrentEntry()
                        }
                        expanded = false
                    },
                    modifier = Modifier.background(
                        color = if (isSelected) BgHover else BgElevated,
                        shape = RoundedCornerShape(size = 6.dp),
                    ),
                ) {
                    Text(
                        text = kind.label(),
                        style = MaterialTheme.typography.body2,
                        color = if (isSelected) PrimaryBlue else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun DiagramViewKind.label(): String {
    return when (this) {
        DiagramViewKind.TREE -> "Rule trees"
        DiagramViewKind.RUN -> "Manifest run"
        DiagramViewKind.OUTCOMES -> "Outcome map"
        DiagramViewKind.FIELDS -> "Field flow"
    }
}

