package ui.workbench.areas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import ruleengine.dsl.ast.RuleAst
import ruleengine.schema.FieldSchemaLoader
import ui.components.ModeTabs
import ui.components.header.AreaHeader
import ui.components.header.model.ActionEmphasis
import ui.components.header.model.BarDensity
import ui.components.header.model.HeaderAction
import ui.diagrams.FieldFlowDiagram
import ui.diagrams.render.DiagramSurface
import ui.dock.DockController
import ui.dock.model.DockSurface
import ui.dock.schemaFieldRange
import ui.editor.rules.RuleEditorState
import ui.project.ProjectWorkspace
import ui.project.model.ProjectFileKind
import ui.schema.FieldSchemaYamlBridge
import ui.schema.IssueLevel
import ui.schema.SchemaIssue
import ui.schema.SchemaIssues
import ui.schema.hasValidationIssues
import ui.workbench.SchemaAreaScreen
import ui.workbench.model.mode.SchemaMode
import ui.workbench.model.mode.displayName
import ui.workbench.model.mode.icon
import ui.yaml.YamlEditor
import ui.yaml.annotateYaml
import ui.yaml.buildYamlCompletions
import ui.yaml.model.YamlEditorType

/**
 * The Schema area: the linked-file header plus the schema editor.
 *
 * Editing the YAML writes three pieces of state — the text, the editor's own field value, and the
 * re-parse — because the table view and the rule editor read different ones.
 */
@Suppress("FunctionNaming")
@Composable
fun SchemaAreaContent(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    /** Which tab is open, and where a click on the other one goes: the workbench view model. */
    mode: SchemaMode,
    onModeChange: (SchemaMode) -> Unit,
    expandedDiagramRules: List<RuleAst>,
    dock: DockController,
    /** The field the Inspector is on, whose lines the dock highlights and whose row is marked. */
    selectedFieldId: String? = null,
    /** How many loaded rules read each field, by dotted path. */
    readBy: Map<String, Int> = emptyMap(),
    /** Where a refused gesture explains itself. */
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Shows one field in the right-hand Inspector; null hides the per-row button. */
    onInspectField: ((path: String) -> Unit)? = null,
) {
    val yaml = state.schemaText.value
    YamlAreaWithDock(
        surface = DockSurface.SCHEMA,
        dock = dock,
        fileName = "schema.yaml",
        yaml = yaml,
        editorType = YamlEditorType.FIELD_SCHEMA,
        highlight = selectedFieldId?.let { path -> schemaFieldRange(yaml = yaml, dottedPath = path) },
        issues = SchemaIssues.of(state = state.schemaEditor.state) + unreadFieldNotes(readBy = readBy),
        onSelectIssue = onInspectField,
        usagesContent = fieldFlow(state = state, rules = expandedDiagramRules),
        // The panel only publishes YAML it could parse, so a blank or duplicate path freezes this text
        // at the last good version. Saying so beats showing text that contradicts the Checks tab.
        staleNotice = if (state.parsedSchema.value == null && yaml.isNotBlank()) {
            "This is the last schema that parsed. Fix the rows below and it will catch up."
        } else {
            null
        },
        modifier = modifier,
    ) {
        SchemaAreaBody(
            state = state,
            workspace = workspace,
            mode = mode,
            // Leaving the visual editor republishes, so the text tab shows what the schema is now
            // rather than what it was when it was last open. It lived in the tab strip's click
            // handler until the strip moved into the shared header.
            onSelectMode = { newMode ->
                if (newMode != mode) {
                    state.schemaEditor.publish(
                        toYaml = { editorState -> FieldSchemaYamlBridge.toYaml(state = editorState) },
                        hasIssues = { editorState -> editorState.hasValidationIssues() },
                        onYamlChange = { newYaml -> state.applySchemaYaml(yaml = newYaml) },
                    )
                }
                onModeChange(newMode)
            },
            onInspectField = onInspectField,
            selectedFieldId = selectedFieldId,
            readBy = readBy,
            onMessage = onMessage,
        )
    }
}

/** The area's own content: the linked-file header and the editor, with no dock concerns. */
@Suppress("FunctionNaming")
@Composable
private fun SchemaAreaBody(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    mode: SchemaMode,
    onSelectMode: (SchemaMode) -> Unit,
    onInspectField: ((path: String) -> Unit)?,
    selectedFieldId: String?,
    readBy: Map<String, Int>,
    onMessage: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SchemaAreaHeader(
            state = state,
            workspace = workspace,
            mode = mode,
            onSelectMode = onSelectMode,
        )
        SchemaAreaScreen(
            sync = state.schemaEditor,
            mode = mode,
            modifier = Modifier.fillMaxSize(),
            onInspectField = onInspectField,
            selectedFieldPath = selectedFieldId,
            readBy = readBy,
            onMessage = onMessage,
            yamlEditor = { value, onValueChange, editorModifier ->
                YamlEditor(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = editorModifier,
                    editorType = YamlEditorType.FIELD_SCHEMA,
                    annotate = { text ->
                        annotateYaml(
                            text = text,
                            editorType = YamlEditorType.FIELD_SCHEMA,
                        )
                    },
                    buildCompletions = { context ->
                        buildYamlCompletions(
                            context = context,
                            editorType = YamlEditorType.FIELD_SCHEMA,
                        )
                    },
                )
            },
        )
    }
}

/** The area's own export, next to the file it exports — the project's copy is in the Save menu. */
private const val ACTION_EXPORT = "export"

/** The area header: what this is, which file it edits, which tab is open, and what can be done to it. */
@Suppress("FunctionNaming")
@Composable
private fun SchemaAreaHeader(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    mode: SchemaMode,
    onSelectMode: (SchemaMode) -> Unit,
) {
    val session = workspace.session.value
    AreaHeader(
        title = "Schema",
        meta = "${state.schemaEditor.state.fields.size} fields",
        binding = linkedFileBinding(
            linkedPath = session?.schemaLink,
            isMissing = session?.missing(kind = ProjectFileKind.SCHEMA) != null,
    ),
        onBindingItem = { id ->
            when (id) {
                BINDING_LINK -> workspace.linkSchema()
                BINDING_UNLINK -> workspace.unlink(kind = ProjectFileKind.SCHEMA)
            }
        },
        tabs = { density ->
            ModeTabs(
                modes = SchemaMode.entries,
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
                label = "Save Schema As…",
                emphasis = ActionEmphasis.OVERFLOW,
            ),
    ),
        onAction = { id ->
            if (id == ACTION_EXPORT) workspace.exportShared(kind = ProjectFileKind.SCHEMA)
        },
    )
}

/**
 * The fields no loaded rule reads.
 *
 * Kept apart from [SchemaIssues] because it is the one verdict the model cannot reach on its own: how
 * many rules read a field is a property of the *rules*, and the schema editor has never seen them.
 */
private fun unreadFieldNotes(readBy: Map<String, Int>): List<SchemaIssue> =
    readBy.filterValues { count -> count == 0 }
        .keys
        .sorted()
        .map { path ->
            SchemaIssue(level = IssueLevel.NOTE, path = path, message = "No loaded rule reads this field.")
        }

/**
 * The field-flow diagram, which now lives in the dock rather than replacing the editor.
 *
 * Here the schema is the subject, so the fields nothing reads are the point.
 */
private fun fieldFlow(state: RuleEditorState, rules: List<RuleAst>): @Composable () -> Unit = {
    DiagramSurface {
        FieldFlowDiagram(
            rules = rules,
            schema = state.ruleSchema,
            entryWide = state.showAllRules.value,
        )
    }
}

/**
 * Applies edited schema YAML: the text, the editor's own field value, and the re-parse.
 *
 * All three, because the table view, the code editor and the rule validator each read a different
 * one — writing only the text leaves the other two showing the previous schema.
 */
internal fun RuleEditorState.applySchemaYaml(yaml: String) {
    schemaText.value = yaml
    schemaFieldValue.value = TextFieldValue(text = yaml)
    parsedSchema.value = runCatching {
        FieldSchemaLoader.loadFromString(content = yaml, nameHint = "schema")
    }.getOrNull()
}
