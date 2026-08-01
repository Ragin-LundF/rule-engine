package ui.workbench.areas

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.samples.SampleGalleryScreen
import ui.samples.loadSample
import ui.samples.model.LoadedSample
import ui.samples.model.SampleDescriptor

/**
 * The Samples gallery. Picking a sample replaces the whole editor and jumps to the Rules area.
 */
@Suppress("FunctionNaming")
@Composable
fun SamplesAreaContent(
    state: RuleEditorState,
    scope: CoroutineScope,
    onSampleApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SampleGalleryScreen(
        onSampleSelected = { descriptor ->
            scope.launch {
                state.applySample(descriptor = descriptor, loaded = loadSample(descriptor))
                onSampleApplied()
            }
        },
        modifier = modifier,
    )
}

/**
 * Replaces the editor's contents with a loaded sample.
 *
 * **The order of these writes matters and must not be rearranged.** Each text field is set before
 * its parse so the debounced validation effect — which keys on the rule text — sees a schema and an
 * action schema that already match the rules it is about to check. Setting the rule text first would
 * run one validation pass against the *previous* sample's schema. Clearing the diagnostics after all
 * three is what stops the old sample's errors flashing up against the new one.
 */
internal fun RuleEditorState.applySample(descriptor: SampleDescriptor, loaded: LoadedSample) {
    schemaText.value = loaded.schemaYaml
    schemaFieldValue.value = TextFieldValue(text = loaded.schemaYaml)
    parsedSchema.value = runCatching {
        FieldSchemaLoader.loadFromString(
            content = loaded.schemaYaml,
            nameHint = descriptor.id,
        )
    }.getOrNull()
    actionSchemaText.value = loaded.actionsYaml
    actionFieldValue.value = TextFieldValue(text = loaded.actionsYaml)
    parsedActionSchema.value = runCatching {
        ActionSchemaLoader.loadFromString(content = loaded.actionsYaml)
    }.getOrNull()
    ruleValue.value = TextFieldValue(text = loaded.rulesText)
    diagnosticsList.value = emptyList()
    diagnosticsText.value = ""
    setStatus(
        msg = "Loaded sample: ${descriptor.name}",
        kind = StatusKind.SUCCESS,
    )
}
