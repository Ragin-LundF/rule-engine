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
import androidx.compose.foundation.layout.fillMaxWidth
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
import ruleengine.core.domain.dto.RuleBranch
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.builder.BuilderToRuleDsl
import ui.builder.FormulaParser
import ui.builder.board.BoardCanvas
import ui.builder.board.CanvasSwitch
import ui.builder.formula.FormulaBar
import ui.builder.model.BuilderRule
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.replaceNodeFromFormula
import ui.builder.model.selection.SelectionStep
import ui.builder.outline.OutlineCanvas
import ui.builder.selection.SelectionResolver
import ui.components.SecondaryButton
import ui.components.ToolbarButton
import ui.copyToClipboard
import ui.diagrams.model.DiagramViewKind
import ui.editor.rules.RuleEditorState
import ui.editor.rules.RuleValidationRunner
import ui.editor.rules.ViewModeToggle
import ui.editor.rules.inheritedVariablesForOpenBuffer
import ui.editor.rules.model.RuleValidationOutcome
import ui.editor.rules.model.StatusKind
import ui.editor.rules.model.ViewMode
import ui.editor.rules.sections.MainEditorContentSection
import ui.editor.rules.validateOpenEntry
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
    allBuilderRules: List<BuilderRule> = emptyList(),
    catalogRules: List<CatalogRule> = emptyList(),
    onRuleSelected: (String) -> Unit = {},
    onAddRule: () -> Unit = {},
    onRenameRule: (oldId: String, newId: String) -> Unit = { _, _ -> },
    catalogActions: List<CatalogActionInfo> = emptyList(),
    onBuilderDslChange: (String) -> Unit = {},
    /** Where a refused builder gesture explains itself; reaches the status bar. */
    onBuilderMessage: (String) -> Unit = {},
    /** What the canvas highlights, and what the Inspector is pointed at. */
    selectedNodeId: String? = null,
    selectedStatementId: String? = null,
    selectedSteps: List<SelectionStep>? = null,
    onSelectNode: (String, List<SelectionStep>) -> Unit = { _, _ -> },
    onSelectStatement: (RuleBranch, String) -> Unit = { _, _ -> },
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
            CenterModeContent(
                viewMode = viewMode,
                state = state,
                builderEditorState = builderEditorState,
                allBuilderRules = allBuilderRules,
                catalogRules = catalogRules,
                catalogActions = catalogActions,
                ruleTreeFiles = ruleTreeFiles,
                diagramGraphicsLayer = diagramGraphicsLayer,
                onRuleModeChange = onRuleModeChange,
                onRuleSelected = onRuleSelected,
                onAddRule = onAddRule,
                onRenameRule = onRenameRule,
                onBuilderDslChange = onBuilderDslChange,
                onBuilderMessage = onBuilderMessage,
                selectedNodeId = selectedNodeId,
                selectedStatementId = selectedStatementId,
                selectedSteps = selectedSteps,
                onSelectNode = onSelectNode,
                onSelectStatement = onSelectStatement,
                onTreeRuleSelected = onTreeRuleSelected,
                testContent = testContent,
            )
        }
    }
}

/**
 * Dispatches to the view for [viewMode].
 *
 * Split out of [CenterEditorPanel] so that function is only the frame — header, divider, body — and the
 * `when` that has to grow a branch for every new view lives on its own. `BUILDER` and `BOARD` share a
 * branch: they are two canvases over one rule, not two views of the panel.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun CenterModeContent(
    viewMode: ViewMode,
    state: RuleEditorState,
    builderEditorState: BuilderEditorState,
    allBuilderRules: List<BuilderRule>,
    catalogRules: List<CatalogRule>,
    catalogActions: List<CatalogActionInfo>,
    ruleTreeFiles: List<RuleTreeFile>,
    diagramGraphicsLayer: GraphicsLayer,
    onRuleModeChange: (RuleMode) -> Unit,
    onRuleSelected: (String) -> Unit,
    onAddRule: () -> Unit,
    onRenameRule: (oldId: String, newId: String) -> Unit,
    onBuilderDslChange: (String) -> Unit,
    onBuilderMessage: (String) -> Unit,
    selectedNodeId: String?,
    selectedStatementId: String?,
    selectedSteps: List<SelectionStep>?,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onTreeRuleSelected: (relativePath: String, ruleId: String) -> Unit,
    testContent: @Composable () -> Unit,
) {
            when (viewMode) {
                ViewMode.BUILDER, ViewMode.BOARD -> BuilderModeContent(
                    boardActive = viewMode == ViewMode.BOARD,
                    onCanvasChange = { board ->
                        onRuleModeChange(if (board) RuleMode.BOARD else RuleMode.BUILDER)
                    },
                    state = state,
                    builderEditorState = builderEditorState,
                    catalogActions = catalogActions,
                    ruleTreeFiles = ruleTreeFiles,
                    allBuilderRules = allBuilderRules,
                    // The open file's problems, not this rule's: a diagnostic carries a file and a line
                    // but no rule id, and the Builder does not know where in the file its rule starts.
                    // Showing the file's is honest and still useful; claiming per-rule precision would
                    // not be.
                    diagnostics = state.diagnosticsList.value.map { diagnostic ->
                        val where = diagnostic.line?.let { line -> "line $line: " }.orEmpty()
                        where + diagnostic.message
                    },
                    onAddRule = onAddRule,
                    onRenameRule = onRenameRule,
                    onBuilderDslChange = onBuilderDslChange,
                    onBuilderMessage = onBuilderMessage,
                    selectedNodeId = selectedNodeId,
                    selectedStatementId = selectedStatementId,
                    selectedSteps = selectedSteps,
                    onSelectNode = onSelectNode,
                    onSelectStatement = onSelectStatement,
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
                ViewMode.TABLE -> TableModeContent(
                    allBuilderRules = allBuilderRules,
                    catalogRules = catalogRules,
                    selectedRuleId = builderEditorState.ruleId,
                    onRuleSelected = onRuleSelected,
                    onRuleModeChange = onRuleModeChange,
                )
            }
}

/**
 * The strip above the canvas: which canvas, and the selected row as text.
 *
 * Above *both* canvases because neither owns it — the switch changes how the rule is drawn and the
 * formula bar edits the selection, and both canvases read and write the same selection. So this belongs
 * to the pair of them rather than to either.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun CanvasToolbar(
    boardActive: Boolean,
    onCanvasChange: (Boolean) -> Unit,
    builderEditorState: BuilderEditorState,
    selectedNodeId: String?,
    onBuilderDslChange: (String) -> Unit,
    onBuilderMessage: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        CanvasSwitch(boardActive = boardActive, onChange = onCanvasChange)
        FormulaBar(
            text = selectedNodeId?.let { id ->
                SelectionResolver.findNode(nodes = builderEditorState.conditionNodes, id = id)
                    ?.let { node -> BuilderToRuleDsl.renderRow(node = node) }
            },
            parse = { text -> FormulaParser.parseCondition(text = text) },
            onApply = { node ->
                val id = selectedNodeId ?: return@FormulaBar
                val replaced = builderEditorState.replaceNodeFromFormula(id = id, parsed = node)
                if (replaced) {
                    BuilderToRuleDsl.generate(state = builderEditorState)?.let(onBuilderDslChange)
                } else {
                    onBuilderMessage("That row could not be replaced.")
                }
            },
            modifier = Modifier.weight(weight = 1f),
        )
    }
}

/** Table mode: every loaded rule at a glance, with a click through to the Builder. */
@Suppress("FunctionNaming")
@Composable
private fun TableModeContent(
    allBuilderRules: List<BuilderRule>,
    catalogRules: List<CatalogRule>,
    selectedRuleId: String,
    onRuleSelected: (String) -> Unit,
    onRuleModeChange: (RuleMode) -> Unit,
) {
    RuleTablePanel(
        allBuilderRules = allBuilderRules,
        catalogRules = catalogRules,
        selectedRuleId = selectedRuleId,
        onRuleClick = { ruleId ->
            onRuleSelected(ruleId)
            onRuleModeChange(RuleMode.BUILDER)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Builder mode: the rule tree on the left, the selected rule's blocks on the right. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun BuilderModeContent(
    boardActive: Boolean,
    onCanvasChange: (Boolean) -> Unit,
    state: RuleEditorState,
    builderEditorState: BuilderEditorState,
    catalogActions: List<CatalogActionInfo>,
    ruleTreeFiles: List<RuleTreeFile>,
    allBuilderRules: List<BuilderRule>,
    diagnostics: List<String>,
    onAddRule: () -> Unit,
    onRenameRule: (oldId: String, newId: String) -> Unit,
    onBuilderDslChange: (String) -> Unit,
    onBuilderMessage: (String) -> Unit,
    selectedNodeId: String?,
    selectedStatementId: String?,
    selectedSteps: List<SelectionStep>?,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onSelectStatement: (RuleBranch, String) -> Unit,
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
        Column(modifier = Modifier.weight(weight = 1f).fillMaxSize()) {
            // The switch sits on the canvas rather than in the mode tabs: both canvases show the same
            // rule with the same selection and the same Inspector, so this changes how it is drawn, not
            // what the centre panel is.
            CanvasToolbar(
                boardActive = boardActive,
                onCanvasChange = onCanvasChange,
                builderEditorState = builderEditorState,
                selectedNodeId = selectedNodeId,
                onBuilderDslChange = onBuilderDslChange,
                onBuilderMessage = onBuilderMessage,
            )

            if (boardActive) {
                BoardCanvas(
                    state = builderEditorState,
                    files = ruleTreeFiles,
                    rules = allBuilderRules,
                    selectedNodeId = selectedNodeId,
                    selectedStatementId = selectedStatementId,
                    onSelectNode = { nodeId -> onSelectNode(nodeId, emptyList()) },
                    onSelectStatement = onSelectStatement,
                    onSelectRule = onTreeRuleSelected,
                    onDslChange = onBuilderDslChange,
                    onMessage = onBuilderMessage,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                OutlineCanvas(
                    state = builderEditorState,
                    catalogActions = catalogActions,
                    selectedNodeId = selectedNodeId,
                    selectedStatementId = selectedStatementId,
                    selectedSteps = selectedSteps,
                    onSelectNode = onSelectNode,
                    onSelectStatement = onSelectStatement,
                    onDslChange = onBuilderDslChange,
                    onMessage = onBuilderMessage,
                    onRenameRule = onRenameRule,
                    diagnostics = diagnostics,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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

            ViewMode.BUILDER, ViewMode.BOARD, ViewMode.TEST, ViewMode.TABLE -> {}
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
    val schema = ruleSchema
    if (schema == null) {
        setStatus(msg = "No schema loaded", kind = StatusKind.ERROR)
        return
    }
    if (ruleText.isBlank()) {
        setStatus(msg = "Rule is empty", kind = StatusKind.IDLE)
        return
    }

    // The whole entry when a project is open, so the click reports what the engine would at load time —
    // including the files the editor is not showing. One open file falls back to validating just that.
    val outcome = validateOpenEntry() ?: RuleValidationRunner.run(
        ruleText = ruleText,
        schema = schema,
        actions = parsedActionSchema.value,
        inheritedVariables = inheritedVariablesForOpenBuffer(),
    )

    when (outcome) {
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

