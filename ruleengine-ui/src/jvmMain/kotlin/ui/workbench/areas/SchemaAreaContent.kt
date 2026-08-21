package ui.workbench.areas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ruleengine.dsl.ast.RuleAst
import ruleengine.schema.FieldSchemaLoader
import ui.diagrams.FieldFlowDiagram
import ui.diagrams.render.DiagramSurface
import ui.editor.rules.RuleEditorState
import ui.project.ProjectWorkspace
import ui.project.dialog.LinkedFileHeader
import ui.project.model.ProjectFileKind
import ui.schema.FieldSchemaYamlBridge
import ui.workbench.SchemaAreaScreen
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
    expandedDiagramRules: List<RuleAst>,
    modifier: Modifier = Modifier,
    /** Shows one field in the right-hand Inspector; null hides the per-row button. */
    onInspectField: ((path: String) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        LinkedFileHeader(
            label = "SCHEMA FILE",
            linkedPath = workspace.session.value?.schemaLink,
            isMissing = workspace.session.value?.missing(kind = ProjectFileKind.SCHEMA) != null,
            onLink = workspace::linkSchema,
            onUnlink = { workspace.unlink(kind = ProjectFileKind.SCHEMA) },
        )
        Spacer(modifier = Modifier.height(height = 10.dp))
        SchemaAreaScreen(
            schemaYaml = state.schemaText.value,
            fromYaml = { yaml ->
                FieldSchemaYamlBridge.fromYaml(yaml = yaml)
            },
            toYaml = { editorState ->
                FieldSchemaYamlBridge.toYaml(state = editorState)
            },
            onSchemaYamlChange = { newYaml -> state.applySchemaYaml(yaml = newYaml) },
            modifier = Modifier.fillMaxSize(),
            // The same field-flow diagram the rule editor shows, filling the "Usages" tab
            // that has been a placeholder: here the schema is the subject, so the fields
            // nothing reads are the point.
            usagesContent = {
                DiagramSurface {
                    FieldFlowDiagram(
                        rules = expandedDiagramRules,
                        schema = state.ruleSchema,
                        entryWide = state.showAllRules.value,
                    )
                }
            },
            onInspectField = onInspectField,
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
