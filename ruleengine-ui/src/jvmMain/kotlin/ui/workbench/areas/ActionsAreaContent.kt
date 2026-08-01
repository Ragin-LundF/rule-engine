package ui.workbench.areas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ruleengine.dsl.ast.RuleAst
import ruleengine.schema.ActionSchemaLoader
import ui.actions.ActionSchemaYamlBridge
import ui.diagrams.OutcomeMapDiagram
import ui.diagrams.render.DiagramSurface
import ui.editor.rules.RuleEditorState
import ui.project.ProjectWorkspace
import ui.project.dialog.LinkedFileHeader
import ui.project.model.ProjectFileKind
import ui.workbench.ActionsAreaScreen
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
    expandedDiagramRules: List<RuleAst>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LinkedFileHeader(
            label = "ACTIONS FILE",
            linkedPath = workspace.session.value?.actionsLink,
            isMissing = workspace.session.value?.missing(kind = ProjectFileKind.ACTIONS) != null,
            onLink = workspace::linkActions,
            onUnlink = { workspace.unlink(kind = ProjectFileKind.ACTIONS) },
        )
        Spacer(modifier = Modifier.height(height = 10.dp))
        ActionsAreaScreen(
            actionsYaml = state.actionSchemaText.value,
            fromYaml = { yaml ->
                ActionSchemaYamlBridge.fromYaml(yaml = yaml)
            },
            toYaml = { editorState ->
                ActionSchemaYamlBridge.toYaml(state = editorState)
            },
            onActionsYamlChange = { newYaml ->
                state.actionSchemaText.value = newYaml
                state.parsedActionSchema.value = runCatching {
                    ActionSchemaLoader.loadFromString(content = newYaml)
                }.getOrNull()
            },
            modifier = Modifier.fillMaxSize(),
            // The outcome map rather than the field flow: in the actions area the question is
            // which rules emit an action and which of them share an output bucket.
            usagesContent = {
                DiagramSurface {
                    OutcomeMapDiagram(rules = expandedDiagramRules)
                }
            },
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
