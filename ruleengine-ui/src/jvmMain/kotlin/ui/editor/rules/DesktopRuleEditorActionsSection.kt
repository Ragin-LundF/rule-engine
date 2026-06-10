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
import ruleengine.core.domain.ActionSchema
import ui.Bg
import ui.BgHover
import ui.BorderColor
import ui.TextSecondary
import ui.YamlEditor
import ui.YamlEditorType
import ui.annotateYaml
import ui.buildYamlCompletions

@Composable
@Suppress("LongParameterList")
fun DesktopRuleEditorActionsSection(
    parsedActionSchema: ActionSchema?,
    actionsExpanded: Boolean,
    actionFieldValue: TextFieldValue,
    onActionFieldValueChange: (TextFieldValue) -> Unit,
    onExample: () -> Unit,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onInsertAction: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            SectionHeader(title = "Action Schema YAML")
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 3.dp))
                    .background(color = BgHover)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 3.dp))
                    .clickable { /* parent toggles */ }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (actionsExpanded) "▲ Collapse" else "▼ Expand",
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (actionsExpanded) 280.dp else 110.dp)
                .clip(shape = RoundedCornerShape(size = 4.dp))
                .background(color = Bg)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 4.dp))
                .padding(all = 8.dp),
        ) {
            YamlEditor(
                value = actionFieldValue,
                onValueChange = onActionFieldValueChange,
                modifier = Modifier.fillMaxSize(),
                editorType = YamlEditorType.ACTION_SCHEMA,
                annotate = { text -> annotateYaml(text = text, editorType = YamlEditorType.ACTION_SCHEMA) },
                buildCompletions = { ctx ->
                    buildYamlCompletions(
                        context = ctx,
                        editorType = YamlEditorType.ACTION_SCHEMA
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
        // Render available actions list
        parsedActionSchema?.let { aschema ->
            SectionHeader(title = "Available Actions")
            aschema.actions.entries.toList().forEach { (name, def) ->
                ActionItem(name = name, def = def) { ins -> onInsertAction(ins) }
            }
        }

        PanelDivider()
    }
}



