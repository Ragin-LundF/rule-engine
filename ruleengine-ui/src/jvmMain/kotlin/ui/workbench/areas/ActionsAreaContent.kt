package ui.workbench.areas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import ruleengine.dsl.ast.RuleAst
import ruleengine.schema.ActionSchemaLoader
import ui.actions.ActionIssues
import ui.actions.ActionSchemaYamlBridge
import ui.actions.hasValidationIssues
import ui.components.ModeTabs
import ui.components.header.AreaHeader
import ui.components.header.model.ActionEmphasis
import ui.components.header.model.BarDensity
import ui.components.header.model.HeaderAction
import ui.diagrams.OutcomeMapDiagram
import ui.diagrams.render.DiagramSurface
import ui.dock.DockController
import ui.dock.actionRange
import ui.dock.model.DockSurface
import ui.editor.rules.RuleEditorState
import ui.project.ProjectWorkspace
import ui.project.model.ProjectFileKind
import ui.schema.IssueLevel
import ui.schema.SchemaIssue
import ui.workbench.ActionsAreaScreen
import ui.workbench.model.mode.ActionMode
import ui.workbench.model.mode.displayName
import ui.workbench.model.mode.icon
import ui.yaml.YamlEditor
import ui.yaml.annotateYaml
import ui.yaml.buildYamlCompletions
import ui.yaml.model.YamlEditorType

/**
 * The Actions area: the linked-file header plus the action-schema editor.
 *
 * Unlike the schema area this writes only the text and the re-parse — the actions editor has no
 * separate field value to keep in step.
 */
@Suppress("FunctionNaming")
@Composable
fun ActionsAreaContent(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    /** Which tab is open, and where a click on the other one goes: the workbench view model. */
    mode: ActionMode,
    onModeChange: (ActionMode) -> Unit,
    expandedDiagramRules: List<RuleAst>,
    dock: DockController,
    /** The action the Inspector is on, whose lines the dock highlights and whose row is marked. */
    selectedActionName: String? = null,
    /** How many loaded rules emit each action. */
    emittedBy: Map<String, Int> = emptyMap(),
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Shows one action in the right-hand Inspector; null hides the per-row button. */
    onInspectAction: ((name: String) -> Unit)? = null,
) {
    val yaml = state.actionSchemaText.value
    YamlAreaWithDock(
        surface = DockSurface.ACTIONS,
        dock = dock,
        fileName = "actions.yaml",
        yaml = yaml,
        editorType = YamlEditorType.ACTION_SCHEMA,
        highlight = selectedActionName?.let { name -> actionRange(yaml = yaml, name = name) },
        issues = ActionIssues.of(state = state.actionEditor.state) + unusedActionNotes(emittedBy = emittedBy),
        onSelectIssue = onInspectAction,
        usagesContent = outcomeMap(rules = expandedDiagramRules),
        staleNotice = if (state.parsedActionSchema.value == null && yaml.isNotBlank()) {
            "This is the last action schema that parsed. Fix the rows below and it will catch up."
        } else {
            null
        },
        modifier = modifier,
    ) {
        ActionsAreaBody(
            state = state,
            workspace = workspace,
            mode = mode,
            // Publish before leaving the visual editor — the mirror of the Schema area.
            onSelectMode = { newMode ->
                if (newMode != mode) {
                    state.actionEditor.publish(
                        toYaml = { editorState -> ActionSchemaYamlBridge.toYaml(state = editorState) },
                        hasIssues = { editorState -> editorState.hasValidationIssues() },
                        onYamlChange = { newYaml -> state.applyActionsYaml(yaml = newYaml) },
                    )
                }
                onModeChange(newMode)
            },
            onInspectAction = onInspectAction,
            selectedActionName = selectedActionName,
            emittedBy = emittedBy,
            onMessage = onMessage,
        )
    }
}

/** The area's own content: the linked-file header and the editor, with no dock concerns. */
@Suppress("FunctionNaming")
@Composable
private fun ActionsAreaBody(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    mode: ActionMode,
    onSelectMode: (ActionMode) -> Unit,
    onInspectAction: ((name: String) -> Unit)?,
    selectedActionName: String?,
    emittedBy: Map<String, Int>,
    onMessage: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ActionsAreaHeader(
            state = state,
            workspace = workspace,
            mode = mode,
            onSelectMode = onSelectMode,
        )
        ActionsAreaScreen(
            sync = state.actionEditor,
            mode = mode,
            modifier = Modifier.fillMaxSize(),
            onInspectAction = onInspectAction,
            selectedActionName = selectedActionName,
            emittedBy = emittedBy,
            onMessage = onMessage,
            yamlEditor = { value, onValueChange, editorModifier ->
                YamlEditor(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = editorModifier,
                    editorType = YamlEditorType.ACTION_SCHEMA,
                    annotate = { text ->
                        annotateYaml(
                            text = text,
                            editorType = YamlEditorType.ACTION_SCHEMA,
                        )
                    },
                    buildCompletions = { context ->
                        buildYamlCompletions(
                            context = context,
                            editorType = YamlEditorType.ACTION_SCHEMA,
                        )
                    },
                )
            },
        )
    }
}

/** The area's own export, next to the file it exports — the project's copy is in the Save menu. */
private const val ACTION_EXPORT = "export"

/** The area header — the mirror of the Schema area's. */
@Suppress("FunctionNaming")
@Composable
private fun ActionsAreaHeader(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    mode: ActionMode,
    onSelectMode: (ActionMode) -> Unit,
) {
    val session = workspace.session.value
    AreaHeader(
        title = "Actions",
        meta = "${state.actionEditor.state.actions.size} actions",
        binding = linkedFileBinding(
            linkedPath = session?.actionsLink,
            isMissing = session?.missing(kind = ProjectFileKind.ACTIONS) != null,
    ),
        onBindingItem = { id ->
            when (id) {
                BINDING_LINK -> workspace.linkActions()
                BINDING_UNLINK -> workspace.unlink(kind = ProjectFileKind.ACTIONS)
            }
        },
        tabs = { density ->
            ModeTabs(
                modes = ActionMode.entries,
                current = mode,
                label = { tabMode -> tabMode.displayName },
                onSelect = onSelectMode,
                icon = { tabMode -> tabMode.icon },
                showLabels = density != BarDensity.MINIMAL,
            )
        },
        actions = listOf(
            HeaderAction(
                id = ACTION_EXPORT,
                label = "Save Actions As…",
                emphasis = ActionEmphasis.OVERFLOW,
            ),
    ),
        onAction = { id ->
            if (id == ACTION_EXPORT) workspace.exportShared(kind = ProjectFileKind.ACTIONS)
        },
    )
}

/**
 * The actions no loaded rule emits.
 *
 * Apart from [ActionIssues] for the same reason the schema's is: how many rules emit an action is a
 * property of the rules, which the action schema has never seen.
 */
private fun unusedActionNotes(emittedBy: Map<String, Int>): List<SchemaIssue> =
    emittedBy.filterValues { count -> count == 0 }
        .keys
        .sorted()
        .map { name ->
            SchemaIssue(level = IssueLevel.NOTE, path = name, message = "No loaded rule emits this action.")
        }

/**
 * The outcome map rather than the field flow: in the actions area the question is which rules emit an
 * action and which of them share an output bucket. Moved into the dock, so seeing it no longer replaces
 * the editor.
 */
private fun outcomeMap(rules: List<RuleAst>): @Composable () -> Unit = {
    DiagramSurface {
        OutcomeMapDiagram(rules = rules)
    }
}

/**
 * Applies edited action YAML: the text, the editor's own field value, and the re-parse.
 *
 * All three, because the visual table, the code editor and the rule validator each read a different
 * one — writing only the text leaves the other two describing the previous schema. The mirror of
 * [ui.workbench.areas.applySchemaYaml].
 */
internal fun RuleEditorState.applyActionsYaml(yaml: String) {
    actionSchemaText.value = yaml
    actionFieldValue.value = TextFieldValue(text = yaml)
    parsedActionSchema.value = runCatching {
        ActionSchemaLoader.loadFromString(content = yaml)
    }.getOrNull()
}
