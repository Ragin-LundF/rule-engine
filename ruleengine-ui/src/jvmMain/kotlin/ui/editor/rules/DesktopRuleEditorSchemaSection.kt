package ui.editor.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.FieldSchema
import ui.Bg
import ui.BgHover
import ui.BorderColor
import ui.TextMuted
import ui.TextSecondary
import ui.YamlEditor
import ui.YamlEditorType
import ui.annotateYaml
import ui.buildYamlCompletions

/**
 * Desktop-only composable that renders the Field Schema YAML editor and the
 * parsed fields list. All side-effecting actions (file pickers, saving,
 * status updates) are executed by caller via the provided lambdas so this
 * component stays focused on UI composition only.
 */
@Composable
@Suppress("LongParameterList")
fun DesktopRuleEditorSchemaSection(
    parsedSchema: FieldSchema?,
    schemaExpanded: Boolean,
    schemaFieldValue: TextFieldValue,
    onSchemaFieldValueChange: (TextFieldValue) -> Unit,
    onExample: () -> Unit,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onInsertField: (String) -> Unit,
) {
    // Render the same structure as the original monolith but delegated here.
    // The caller keeps the canonical state and provides callbacks for actions.
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            SectionHeader(title = "Field Schema YAML")
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 3.dp))
                    .background(color = BgHover)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 3.dp))
                    .clickable { /* toggle handled by parent state */ }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                // Parent controls the expanded state and the toggle button label.
                Text(
                    text = if (schemaExpanded) "▲ Collapse" else "▼ Expand",
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = if (schemaExpanded) 320.dp else 140.dp)
                .clip(shape = RoundedCornerShape(size = 4.dp))
                .background(color = Bg)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 4.dp))
                .padding(all = 8.dp),
        ) {
            YamlEditor(
                value = schemaFieldValue,
                onValueChange = onSchemaFieldValueChange,
                modifier = Modifier.fillMaxSize(),
                editorType = YamlEditorType.FIELD_SCHEMA,
                annotate = { text -> annotateYaml(text = text, editorType = YamlEditorType.FIELD_SCHEMA) },
                buildCompletions = { ctx ->
                    buildYamlCompletions(
                        context = ctx,
                        editorType = YamlEditorType.FIELD_SCHEMA
                    )
                },
                placeholder = "# Click here — completions appear automatically\n" +
                        "# or press Example to start from a template",
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton(label = "Example") { onExample() }
            AppButton(label = "Load") { onLoad() }
            AppButton(label = "Save") { onSave() }
            AppButton(label = "Clear", danger = true) { onClear() }
        }
        Spacer(Modifier.height(8.dp))
        // Render parsed fields list (caller provides parsedSchema)
        if (parsedSchema != null) {
            SectionHeader(title = "Fields")
            parsedSchema.fields.entries.toList().forEach { (fid, def) ->
                FieldItem(id = fid, def = def) { ins -> onInsertField(ins) }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape = RoundedCornerShape(size = 6.dp))
                    .background(color = Bg)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
                    .padding(all = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Load or paste a field schema to see fields",
                    style = MaterialTheme.typography.body2,
                    color = TextMuted
                )
            }
        }

        PanelDivider()
    }
}



