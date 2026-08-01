package ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ui.TextMuted

/** Shown under every code editor, because the completion popup no longer appears on its own. */
const val AUTOCOMPLETE_HINT = "Ctrl + Space for autocomplete"

/**
 * Hosts a YAML editor and owns its caret.
 *
 * The schema and action panels used to hand the editor a freshly built `TextFieldValue(text = yaml)`
 * on every recomposition and pass only `newValue.text` back. A `TextFieldValue` built that way
 * carries the default selection — offset zero — so the caret jumped to the start of the document
 * after every keystroke, every character landed at the front, and clicking a line had no lasting
 * effect because the next recomposition threw the selection away. The caret therefore has to live
 * here, in state, and only the *text* may come from the caller.
 */
@Suppress("FunctionNaming")
@Composable
fun YamlEditorPane(
    yaml: String,
    onYamlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editor: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
    ) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(text = yaml)) }

    // Re-sync only when the text genuinely changed elsewhere — switching away from the visual
    // editor regenerates it. Typing does not come through here, so the caret survives it.
    LaunchedEffect(yaml) {
        if (value.text != yaml) {
            val caret = value.selection.start.coerceIn(0, yaml.length)
            value = TextFieldValue(text = yaml, selection = TextRange(index = caret))
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        editor(
            value,
            { newValue ->
                value = newValue
                if (newValue.text != yaml) {
                    onYamlChange(newValue.text)
                }
            },
            Modifier.fillMaxWidth().weight(weight = 1f),
        )
        Text(
            text = AUTOCOMPLETE_HINT,
            style = MaterialTheme.typography.caption,
            color = TextMuted,
        )
    }
}
