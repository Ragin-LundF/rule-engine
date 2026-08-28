package ui.editor.yaml

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * The state of a visual editor that round-trips its model through YAML.
 *
 * One class for the Schema and Actions editors, which had a copy each — identical but for the type,
 * with a comment on the second pointing at the first for the explanation. The Manifest area is not
 * here: it edits the project session directly and its text is only a fallback.
 *
 * **Held outside the panel that draws it.** It used to be `remember`ed inside `SchemaEditorPanel`, which
 * made it unreachable from the Inspector — and the Inspector is now the editing surface. Both read this
 * one object, so they cannot disagree about what the schema currently is.
 *
 * **The mode is not here.** Which tab an area is on is workbench state, held by `RuleWorkbenchState` and
 * changed by dispatching an action, the same as every other navigation choice. It lived here until the
 * header work, which meant three areas each owned their own mode while the view model held three fields
 * nobody read — two sources of truth, one of them a decoy.
 *
 * [loaded] is the model as it was last read from YAML, and it is what distinguishes "the user changed
 * something" from "the panel merely parsed the file". Regenerating YAML is lossy — comments, blank lines
 * and quoting style are the author's and the serializer does not keep them — so without it, simply
 * opening a tab would rewrite the file on the next save.
 */
class YamlModelSync<M>(yaml: String, state: M) {
    var state: M by mutableStateOf(value = state)
    var yaml: String by mutableStateOf(value = yaml)
    var error: String? by mutableStateOf(value = null)
    var loaded: M by mutableStateOf(value = state)

    /**
     * Regenerates the YAML for the current model, unless the model has problems that would make the
     * result a lie.
     *
     * Used when leaving the visual editor for the text: the text tab must show what the model is now,
     * not what it was when the tab was last open.
     */
    fun publish(toYaml: (M) -> String, hasIssues: (M) -> Boolean, onYamlChange: (String) -> Unit) {
        if (hasIssues(state)) return
        val generated = runCatching { toYaml(state) }.getOrNull() ?: return
        yaml = generated
        onYamlChange(generated)
    }
}

/**
 * Keeps the model and the YAML text in step, in whichever direction the edit came from.
 *
 * Three effects, and they are not interchangeable:
 *
 * - **an external YAML change pulls in** — a project load, a sample, or the Code view — and resets
 *   [YamlModelSync.loaded] so the pull does not then look like an edit;
 * - **a model change pushes out**, but only when the model is valid *and* actually different from what
 *   was loaded. A model with a blank or duplicate key is skipped rather than serialized, because the
 *   writer drops such entries and the push would delete the row the author is still typing;
 * - **a YAML edit parses back**, debounced, because half-typed YAML is unparseable almost all of the
 *   time and reporting that per keystroke makes the tab unusable.
 *
 * Call this where it is always composed, not inside the area panel: the Inspector can edit a field
 * while another area is on screen, and an effect that is not composed cannot push that edit anywhere.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun <M> SyncModelAndYaml(
    sync: YamlModelSync<M>,
    yaml: String,
    /** Whether the area is showing the file's text rather than the model. */
    textMode: Boolean,
    fromYaml: (String) -> M?,
    toYaml: (M) -> String,
    hasIssues: (M) -> Boolean,
    isReadOnly: (M) -> Boolean,
    onYamlChange: (String) -> Unit,
    parseError: String,
) {
    LaunchedEffect(key1 = yaml) {
        if (yaml != sync.yaml) {
            sync.yaml = yaml
            val parsed = runCatching { fromYaml(yaml) }.getOrNull()
            if (parsed != null) {
                sync.state = parsed
                sync.loaded = parsed
            }
            sync.error = null
        }
    }

    LaunchedEffect(key1 = sync.state, key2 = textMode) {
        if (textMode) return@LaunchedEffect
        if (hasIssues(sync.state)) return@LaunchedEffect
        if (sync.state == sync.loaded) return@LaunchedEffect
        val generated = runCatching { toYaml(sync.state) }.getOrNull() ?: return@LaunchedEffect
        if (generated != sync.yaml) {
            sync.yaml = generated
            onYamlChange(generated)
        }
    }

    LaunchedEffect(key1 = sync.yaml, key2 = textMode) {
        if (!textMode) return@LaunchedEffect
        delay(timeMillis = YAML_PARSE_DELAY_MS)
        val parsed = runCatching { fromYaml(sync.yaml) }.getOrNull()
        if (parsed != null && !isReadOnly(parsed)) {
            sync.state = parsed
            sync.error = null
        } else {
            sync.error = parseError
        }
    }
}

private const val YAML_PARSE_DELAY_MS = 500L
