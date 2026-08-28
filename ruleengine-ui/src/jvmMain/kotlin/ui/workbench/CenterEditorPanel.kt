package ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
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
import ui.builder.formula.FormulaBar
import ui.builder.model.BuilderRule
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.replaceNodeFromFormula
import ui.builder.model.selection.SelectionStep
import ui.builder.outline.OutlineCanvas
import ui.builder.selection.SelectionResolver
import ui.components.ModeTabs
import ui.components.ToolbarButton
import ui.components.header.AreaHeader
import ui.components.header.model.ActionEmphasis
import ui.components.header.model.BarDensity
import ui.components.header.model.BindingMenuItem
import ui.components.header.model.BindingSpec
import ui.components.header.model.HeaderAction
import ui.copyToClipboard
import ui.diagrams.model.DiagramViewKind
import ui.editor.rules.RuleEditorState
import ui.editor.rules.RuleValidationRunner
import ui.editor.rules.inheritedVariablesForOpenBuffer
import ui.editor.rules.model.RuleValidationOutcome
import ui.editor.rules.model.StatusKind
import ui.editor.rules.model.ViewMode
import ui.editor.rules.sections.MainEditorContentSection
import ui.editor.rules.validateOpenEntry
import ui.pickRuleFile
import ui.saveDiagramAsPng
import ui.workbench.export.RuleOverviewExport
import ui.workbench.export.exportRuleOverview
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.catalog.RuleTreeFile
import ui.workbench.model.mode.RuleMode
import ui.workbench.model.mode.displayName
import ui.workbench.model.mode.icon
import ui.workbench.rules.RuleTablePanel
import ui.workbench.rules.toViewMode

/**
 * Which rule file the Rules area is bound to, as the binding chip every area now has.
 *
 * This was an unlabelled `☰` button in the action row — the same job as the Schema and Actions areas'
 * full-width linked-file bar, in a control that named neither the file nor itself. The menu it opens is
 * the one it always opened, including "All files" under the same condition: it is only meaningful where
 * the view can show more than one file at once, and only when the entry has more than one.
 */
private fun ruleFileBinding(
    ruleFiles: List<String>,
    selectedFile: String?,
    showAllRules: Boolean,
    offersAllFiles: Boolean,
): BindingSpec? {
    if (ruleFiles.isEmpty()) return null

    val items = buildList {
        if (offersAllFiles) {
            add(
                element = BindingMenuItem(
                    id = ALL_RULE_FILES,
                    label = "All files",
                    selected = showAllRules,
                    sectionTitle = "Rule files",
                ),
            )
        }
        ruleFiles.forEachIndexed { index, relativePath ->
            add(
                element = BindingMenuItem(
                    id = relativePath,
                    label = relativePath.substringAfterLast(delimiter = '/'),
                    selected = !showAllRules && relativePath == selectedFile,
                    separatorBefore = offersAllFiles && index == 0,
                    sectionTitle = if (offersAllFiles || index > 0) null else "Rule files",
                ),
            )
        }
    }

    val value = when {
        showAllRules -> "All files"
        selectedFile != null -> selectedFile.substringAfterLast(delimiter = '/')
        else -> ruleFiles.first().substringAfterLast(delimiter = '/')
    }

    return BindingSpec(label = "File", value = value, items = items)
}

/** The chip's id for "every rule file of this entry at once"; anything else is a relative path. */
private const val ALL_RULE_FILES = "*all*"

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
            .background(color = BgElevated),
    ) {
        RulesAreaHeader(
            state = state,
            scope = scope,
            ruleMode = ruleMode,
            onRuleModeChange = onRuleModeChange,
            ruleCount = allBuilderRules.size,
            diagramGraphicsLayer = diagramGraphicsLayer,
        )

        Box(modifier = Modifier.weight(weight = 1f).padding(all = 16.dp)) {
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
                    state = state,
                    builderEditorState = builderEditorState,
                    catalogActions = catalogActions,
                    ruleTreeFiles = ruleTreeFiles,
                    allBuilderRules = allBuilderRules,
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
 * The strip above the canvas: the selected row as text.
 *
 * Above *both* canvases because neither owns it — both read and write the same selection. The canvas
 * switch that used to share this row now sits in the area header beside the other view switches, so
 * every switch in the app is in one place; what is left here edits the selection, which is canvas work.
 */
@Suppress("FunctionNaming")
@Composable
private fun FormulaRow(
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
    state: RuleEditorState,
    builderEditorState: BuilderEditorState,
    catalogActions: List<CatalogActionInfo>,
    ruleTreeFiles: List<RuleTreeFile>,
    allBuilderRules: List<BuilderRule>,
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
            FormulaRow(
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The area header for the Rules area — the same header the other three areas have.
 *
 * What it replaces: a "Rule Editor" title over a bordered icon toggle, with a *second* row of buttons
 * underneath it. The second row existed because the tabs and the actions competed for one line and the
 * actions absorbed every shortfall, until the last button's label wrapped one letter per line. The
 * shared header ranks the actions instead — the primary verb keeps its label at any width, the
 * secondary ones fall back to their icons, and the rare ones were never on the bar.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RulesAreaHeader(
    state: RuleEditorState,
    scope: CoroutineScope,
    ruleMode: RuleMode,
    onRuleModeChange: (RuleMode) -> Unit,
    ruleCount: Int,
    diagramGraphicsLayer: GraphicsLayer,
) {
    val viewMode = ruleMode.toViewMode()
    var ruleValue by state.ruleValue
    val entryRuleFiles = entryRuleFiles(state = state)

    AreaHeader(
        title = "Rules",
        meta = rulesMeta(ruleCount = ruleCount, fileCount = entryRuleFiles.size),
        binding = ruleFileBinding(
            ruleFiles = entryRuleFiles,
            selectedFile = state.selectedManifestRuleFile.value,
            showAllRules = state.showAllRules.value,
            // "All files" is only meaningful where the view can show more than one at once, and only
            // when the entry actually has more than one — unchanged from the `☰` menu this replaces.
            offersAllFiles = entryRuleFiles.size >= 2 &&
                (viewMode == ViewMode.DIAGRAM || viewMode == ViewMode.TEST),
        ),
        onBindingItem = { id ->
            if (id == ALL_RULE_FILES) {
                state.loadAllRuleFilesForCurrentEntry()
            } else {
                state.loadSingleManifestRuleFile(id)
            }
        },
        tabs = { density ->
            ModeTabs(
                modes = RULE_TABS,
                // The board has no tab of its own: it is the Visual tab drawn the other way, so that is
                // the tab that has to look selected while it is showing.
                current = if (ruleMode == RuleMode.BOARD) RuleMode.BUILDER else ruleMode,
                label = { mode -> mode.displayName },
                onSelect = onRuleModeChange,
                icon = { mode -> mode.icon },
                // Five tabs is the widest strip in the app, so it degrades in two steps rather than
                // one: the glyphs go first and the words only after them, because "Diagram" teaches
                // and "⬡" does not.
                showIcons = density == BarDensity.FULL,
                showLabels = density != BarDensity.MINIMAL,
            )
        },
        subTabs = rulesSubTabs(
            state = state,
            viewMode = viewMode,
            onRuleModeChange = onRuleModeChange,
        ),
        actions = rulesActions(state = state, viewMode = viewMode),
        onAction = { id ->
            runRulesAction(
                id = id,
                state = state,
                scope = scope,
                ruleValue = ruleValue,
                onRuleValueChange = { value -> ruleValue = value },
                diagramGraphicsLayer = diagramGraphicsLayer,
            )
        },
        // The Rules strip is five tabs wide with a sub-switch beside it, so it needs a good 300 dp more
        // than a two-tab header before it can hold every label. Measured, not guessed: below this the
        // labels are what pushes the sub-switch off the edge.
        fullWidth = RULES_FULL_WIDTH,
        compactWidth = RULES_COMPACT_WIDTH,
    )
}

/** The tabs the Rules area offers, in order. [RuleMode.BOARD] is deliberately not one of them. */
private val RULE_TABS: List<RuleMode> = listOf(
    RuleMode.BUILDER,
    RuleMode.CODE,
    RuleMode.DIAGRAM,
    RuleMode.TEST,
    RuleMode.TABLE,
)

/**
 * What the Rules header needs, measured against the *panel* — not the window.
 *
 * The centre panel gives up a rail and, usually, the Inspector, so a 1440 px window leaves it about
 * 1010 dp — and the widest mode, Code, wants a little more than that for five labelled tabs, the file
 * chip, two secondary verbs and a primary one. So the labels come back at 1080 and the tabs spend the
 * common case as glyphs, rather than the last tab being clipped at the width most windows actually
 * are. Measured against the panel, never the window.
 */
private val RULES_FULL_WIDTH: Dp = 1_080.dp
private val RULES_COMPACT_WIDTH: Dp = 800.dp

/** The rule files of the entry being edited, which is what the chip's menu lists. */
private fun entryRuleFiles(state: RuleEditorState): List<String> {
    return state.parsedManifest.value
        ?.entries
        ?.find { entry -> entry.id == state.selectedManifestEntry.value }
        ?.rules
        .orEmpty()
}

/** "14 rules · 4 files", or as much of it as there is to say. */
private fun rulesMeta(ruleCount: Int, fileCount: Int): String? {
    if (ruleCount == 0) return null
    val rules = "$ruleCount rule" + if (ruleCount == 1) "" else "s"
    if (fileCount <= 1) return rules
    return "$rules · $fileCount files"
}

/**
 * The switch that lives *within* a mode rather than between modes.
 *
 * Outline/Board while the Visual tab is showing — it changes how one rule is drawn, not what the panel
 * is, which is why it is styled as subordinate rather than as a sixth tab. It used to float on the
 * canvas itself; here it is beside the other view switches, where a reader looks for it.
 *
 * In Diagram mode the same slot holds which diagram is drawn, which is the same kind of choice.
 */
private fun rulesSubTabs(
    state: RuleEditorState,
    viewMode: ViewMode,
    onRuleModeChange: (RuleMode) -> Unit,
): (@Composable (BarDensity) -> Unit)? {
    return when (viewMode) {
        // No icons on this pair, deliberately: two words are narrower than two words plus two glyphs,
        // and this is the switch that has to give way first when the bar is short. Words rather than
        // glyphs because "Board" is a thing to learn, and an unfamiliar glyph teaches nobody.
        ViewMode.BUILDER, ViewMode.BOARD -> { _ ->
            ModeTabs(
                modes = CANVAS_TABS,
                current = if (viewMode == ViewMode.BOARD) RuleMode.BOARD else RuleMode.BUILDER,
                label = { mode -> canvasLabel(mode = mode) },
                onSelect = onRuleModeChange,
                subordinate = true,
            )
        }

        ViewMode.DIAGRAM -> { _ -> DiagramViewPicker(state = state) }
        ViewMode.CODE, ViewMode.TEST, ViewMode.TABLE -> null
    }
}

private val CANVAS_TABS: List<RuleMode> = listOf(RuleMode.BUILDER, RuleMode.BOARD)

private fun canvasLabel(mode: RuleMode): String {
    return if (mode == RuleMode.BOARD) "Board" else "Outline"
}

/** The action ids the Rules header reports. */
private const val ACTION_LOAD = "load"
private const val ACTION_COPY = "copy"
private const val ACTION_VALIDATE = "validate"
private const val ACTION_EXPORT_PNG = "export-png"
private const val ACTION_EXPAND = "expand"
private const val ACTION_EXPORT_PREFIX = "export-overview:"

/**
 * What each mode offers, ranked.
 *
 * `Validate` is the primary verb wherever a rule is being written — the Builder generates the same DSL
 * the code view holds, so "is this valid" is the same question in both. It is also the only one with a
 * glyph: `✓` reads at 12 sp, while every download, open and expand arrow available at this size is a
 * hairline. The rest keep their words and move into the `⋯` menu when the bar is short.
 */
private fun rulesActions(state: RuleEditorState, viewMode: ViewMode): List<HeaderAction> {
    return when (viewMode) {
        ViewMode.BUILDER, ViewMode.BOARD -> exportOverviewActions(state = state) + validateAction()

        ViewMode.CODE -> listOf(
            HeaderAction(id = ACTION_LOAD, label = "Load rule…"),
            HeaderAction(id = ACTION_COPY, label = "Copy rule"),
        ) + exportOverviewActions(state = state) + validateAction()

        ViewMode.DIAGRAM -> listOf(
            HeaderAction(id = ACTION_EXPORT_PNG, label = "Export PNG"),
            HeaderAction(id = ACTION_EXPAND, label = "Expand"),
        )

        ViewMode.TEST, ViewMode.TABLE -> emptyList()
    }
}

private fun validateAction(): List<HeaderAction> {
    return listOf(
        HeaderAction(
            id = ACTION_VALIDATE,
            label = "Validate",
            icon = "✓",
            emphasis = ActionEmphasis.PRIMARY,
        ),
    )
}

/**
 * One overflow entry per format.
 *
 * Disabled rather than dropped when no entry is selected: the action exists for every project, and an
 * entry that vanishes from a menu reads as a feature that is missing rather than one not applicable.
 */
private fun exportOverviewActions(state: RuleEditorState): List<HeaderAction> {
    val hasEntry = state.selectedManifestEntry.value != null
    return RuleOverviewExport.Format.entries.map { format ->
        HeaderAction(
            id = ACTION_EXPORT_PREFIX + format.name,
            label = "Export overview — ${format.label}",
            emphasis = ActionEmphasis.OVERFLOW,
            enabled = hasEntry,
        )
    }
}

/** Runs what the header reported. Every branch is the behaviour the old button had. */
@Suppress("LongParameterList")
private fun runRulesAction(
    id: String,
    state: RuleEditorState,
    scope: CoroutineScope,
    ruleValue: TextFieldValue,
    onRuleValueChange: (TextFieldValue) -> Unit,
    diagramGraphicsLayer: GraphicsLayer,
) {
    when {
        id == ACTION_LOAD -> scope.launch { loadRuleFile(state = state, onRuleValueChange = onRuleValueChange) }
        id == ACTION_COPY -> copyRule(state = state, ruleValue = ruleValue)
        id == ACTION_VALIDATE -> scope.launch { state.validateNow(ruleText = state.ruleValue.value.text) }
        id == ACTION_EXPORT_PNG -> scope.launch {
            exportDiagramPng(state = state, diagramGraphicsLayer = diagramGraphicsLayer)
        }

        id == ACTION_EXPAND -> state.showExpandedDiagram.value = true
        id.startsWith(prefix = ACTION_EXPORT_PREFIX) -> {
            val format = RuleOverviewExport.Format.valueOf(id.removePrefix(prefix = ACTION_EXPORT_PREFIX))
            scope.launch { exportRuleOverview(state = state, format = format) }
        }
    }
}

/**
 * Loads a rule file into the buffer.
 *
 * There is deliberately no "Save rule" beside it: rule files are written by Save Project along with the
 * manifest that indexes them. A separate write also went behind the project's back, leaving it convinced
 * the file had been changed by someone else the next time it saved.
 */
private suspend fun loadRuleFile(state: RuleEditorState, onRuleValueChange: (TextFieldValue) -> Unit) {
    val content = pickRuleFile()
    if (content != null) {
        onRuleValueChange(TextFieldValue(text = content))
        state.setStatus(msg = "Rule loaded", kind = StatusKind.SUCCESS)
    } else {
        state.setStatus(msg = "Load cancelled", kind = StatusKind.IDLE)
    }
}

private fun copyRule(state: RuleEditorState, ruleValue: TextFieldValue) {
    if (ruleValue.text.isNotBlank()) {
        copyToClipboard(ruleValue.text)
        state.setStatus(msg = "Rule copied to clipboard", kind = StatusKind.SUCCESS)
    } else {
        state.setStatus(msg = "Nothing to copy", kind = StatusKind.IDLE)
    }
}

private suspend fun exportDiagramPng(state: RuleEditorState, diagramGraphicsLayer: GraphicsLayer) {
    runCatching {
        val bitmap = diagramGraphicsLayer.toImageBitmap()
        saveDiagramAsPng(bitmap = bitmap)
        state.setStatus(msg = "Diagram exported as PNG", kind = StatusKind.SUCCESS)
    }.onFailure { cause ->
        state.setStatus(msg = "Export failed: ${cause.message}", kind = StatusKind.ERROR)
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
        ToolbarButton(label = "View: ${current.label()} ▼", onClick = { expanded = true })
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

