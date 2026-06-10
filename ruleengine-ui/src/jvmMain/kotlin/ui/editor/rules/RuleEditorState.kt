package ui.editor.rules

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import ruleengine.manifest.ProjectManifest
import ruleengine.core.errors.ValidationDiagnostic
import ui.DslCursorContext
import ui.DslSection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldSchema

/**
 * Centralized state holder for the Rule Editor.
 * All UI state is stored here as MutableState so it can be passed around
 * or hoisted into other components easily.
 */
class RuleEditorState(
    val scope: CoroutineScope,
) {
    // Text fields
    val schemaText: MutableState<String> = mutableStateOf("")
    val schemaFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(text = ""))
    val ruleValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(text = ""))

    // Status
    val status: MutableState<String> = mutableStateOf("Ready")
    val statusKind: MutableState<StatusKind> = mutableStateOf(StatusKind.IDLE)

    // Parsed schema/action
    val parsedSchema: MutableState<FieldSchema?> = mutableStateOf(null)
    val actionSchemaText: MutableState<String> = mutableStateOf("")
    val actionFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(text = ""))
    val parsedActionSchema: MutableState<ActionSchema?> = mutableStateOf(null)

    // Manifest
    val manifestText: MutableState<String> = mutableStateOf("")
    val manifestBaseDir: MutableState<String?> = mutableStateOf(null)
    val parsedManifest: MutableState<ProjectManifest?> = mutableStateOf(null)
    val selectedManifestEntry: MutableState<String?> = mutableStateOf(null)

    // Diagnostics
    val diagnosticsList: MutableState<List<ValidationDiagnostic>> = mutableStateOf(emptyList())
    val diagnosticsText: MutableState<String> = mutableStateOf("")

    // Expand/collapse
    val schemaExpanded: MutableState<Boolean> = mutableStateOf(false)
    val actionsExpanded: MutableState<Boolean> = mutableStateOf(false)
    val showManifestYaml: MutableState<Boolean> = mutableStateOf(false)

    // Editor UX
    val cursorRect: MutableState<Rect> = mutableStateOf(Rect.Zero)
    val showAutoComplete: MutableState<Boolean> = mutableStateOf(false)
    val autoCompleteIndex: MutableState<Int> = mutableStateOf(0)
    val autoCompleteWord: MutableState<String> = mutableStateOf("")
    val autoCompleteWordStart: MutableState<Int> = mutableStateOf(0)
    val dslContext: MutableState<DslCursorContext> = mutableStateOf(DslCursorContext(section = DslSection.TOP_LEVEL))
    val splitFraction: MutableState<Float> = mutableStateOf(0.33f)
    val viewMode: MutableState<ViewMode> = mutableStateOf(ViewMode.CODE)
    val showExpandedDiagram: MutableState<Boolean> = mutableStateOf(false)

    // Parsed rules for diagram are derived in UI; kept as helper nullable here if needed

    // Helper methods
    fun setStatus(msg: String, kind: StatusKind) {
        status.value = msg
        statusKind.value = kind
    }
}

// end
