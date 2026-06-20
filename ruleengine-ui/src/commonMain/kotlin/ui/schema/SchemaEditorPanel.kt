package ui.schema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.components.SectionTitle

import ui.workbench.SchemaMode

/**
 * Visual field-schema editor with Visual / YAML / Usages tabs.
 *
 * Accepts the current [yaml] text and conversion helpers. On every edit it calls
 * [onYamlChange] with the regenerated YAML so the rest of the app can reload the schema.
 */
@Composable
fun SchemaEditorPanel(
    yaml: String,
    fromYaml: (String) -> SchemaEditorState,
    toYaml: (SchemaEditorState) -> String,
    onYamlChange: (String) -> Unit,
    initialMode: SchemaMode = SchemaMode.VISUAL,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var editorState by remember(yaml) {
        mutableStateOf(fromYaml(yaml))
    }
    var yamlText by remember(yaml) { mutableStateOf(yaml) }
    var yamlError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = editorState) {
        val generated = runCatching { toYaml(editorState) }.getOrNull()
        if (generated != null && generated != yamlText) {
            yamlText = generated
            onYamlChange(generated)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SchemaModeTabs(
            current = mode,
            onSelect = { mode = it },
        )

        when (mode) {
            SchemaMode.VISUAL -> VisualSchemaEditor(
                state = editorState,
                onStateChange = { editorState = it },
            )
            SchemaMode.YAML -> YamlSchemaEditor(
                yaml = yamlText,
                error = yamlError,
                onYamlChange = {
                    yamlText = it
                    yamlError = null
                },
                onReload = {
                    val parsed = runCatching {
                        fromYaml(yamlText)
                    }.getOrElse { _ ->
                        null
                    }
                    if (parsed != null) {
                        editorState = parsed
                        yamlError = null
                        onYamlChange(yamlText)
                    } else {
                        yamlError = "Invalid YAML: could not parse schema"
                    }
                },
            )
            SchemaMode.USAGES -> SchemaUsagesPanel()
        }
    }
}

@Composable
private fun VisualSchemaEditor(
    state: SchemaEditorState,
    onStateChange: (SchemaEditorState) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "FIELDS")
        FieldSchemaTable(
            state = state,
            onStateChange = onStateChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun YamlSchemaEditor(
    yaml: String,
    error: String?,
    onYamlChange: (String) -> Unit,
    onReload: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(text = "YAML")
            OutlinedButton(onClick = onReload) {
                Text(text = "Reload from YAML", style = MaterialTheme.typography.caption)
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.error,
            )
        }
        OutlinedTextField(
            value = yaml,
            onValueChange = onYamlChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.body2,
        )
    }
}

@Composable
private fun SchemaUsagesPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "USAGE")
        Text(
            text = "Field usage across rules will be shown here in a later phase.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}
