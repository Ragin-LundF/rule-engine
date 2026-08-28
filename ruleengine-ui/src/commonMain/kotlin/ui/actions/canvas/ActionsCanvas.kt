package ui.actions.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.AccentPurple
import ui.AccentRed
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextMuted
import ui.TextSecondary
import ui.actions.ActionIssues
import ui.actions.model.ActionEditorState
import ui.actions.model.EditableAction
import ui.components.TinyButton
import ui.schema.IssueLevel
import ui.schema.SchemaIssue

/**
 * The action schema as an outline: one line per action, written as the call a rule makes.
 *
 * `decision(string)` rather than a name beside a row of tick-boxes, because the signature is the thing:
 * `argTypes` declares **how many** arguments and **the type of each, in order**, and a set of chips can
 * say neither. Reading the declaration the way a rule writes it is what makes an arity mismatch visible
 * before the rule is written rather than after it fails to compile.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun ActionsCanvas(
    state: ActionEditorState,
    onStateChange: (ActionEditorState) -> Unit,
    modifier: Modifier = Modifier,
    /** The action the Inspector is on, highlighted here. */
    selectedName: String? = null,
    onSelectName: (String) -> Unit = {},
    /** How many loaded rules emit each action. Empty when nothing is parsed. */
    emittedBy: Map<String, Int> = emptyMap(),
    onMessage: (String) -> Unit = {},
) {
    val editable = !state.isReadOnly

    Column(modifier = modifier.fillMaxSize()) {
        if (state.isReadOnly) {
            Text(
                text = "Read-only: the YAML uses constructs this editor cannot round-trip. " +
                    "Edit the text directly.",
                style = MaterialTheme.typography.caption,
                color = AccentOrange,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        CanvasHeader(
            editable = editable,
            onAdd = {
                val name = uniqueName(taken = state.actions.map { it.name }, base = "action")
                onStateChange(
                    state.copy(
                        actions = state.actions + EditableAction(name = name, argTypes = listOf("string")),
                    ),
                )
                onSelectName(name)
            },
        )

        ActionList(
            state = state,
            selectedName = selectedName,
            editable = editable,
            emittedBy = emittedBy,
            onSelectName = onSelectName,
            onStateChange = onStateChange,
            onMessage = onMessage,
            modifier = Modifier.fillMaxWidth().weight(weight = 1f),
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ActionList(
    state: ActionEditorState,
    selectedName: String?,
    editable: Boolean,
    emittedBy: Map<String, Int>,
    onSelectName: (String) -> Unit,
    onStateChange: (ActionEditorState) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.verticalScroll(state = rememberScrollState())) {
        state.actions.forEachIndexed { index, action ->
            ActionRow(
                action = action,
                ordinal = index + 1,
                selected = action.name == selectedName,
                editable = editable,
                emitted = emittedBy[action.name],
                onSelect = { onSelectName(action.name) },
                onRemove = {
                    val blocked = ActionCanvasGuards.blockedRemoval(
                        name = action.name,
                        emittedBy = emittedBy[action.name] ?: 0,
                    )
                    if (blocked != null) {
                        onMessage(blocked)
                    } else {
                        onStateChange(state.copy(actions = state.actions.filterIndexed { at, _ -> at != index }))
                    }
                },
            )
        }
        if (state.actions.isEmpty()) {
            Text(
                text = "(no actions yet — a rule's then block has nothing it may emit)",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.padding(start = 30.dp, top = 6.dp),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CanvasHeader(editable: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "ACTIONS",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = PrimaryBlue,
        )
        Text(
            text = "the output vocabulary — arguments are positional",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.weight(weight = 1f),
        )
        if (editable) {
            TinyButton(text = "+ action", onClick = onAdd)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "signature · purpose",
            style = MaterialTheme.typography.caption,
            color = TextMuted,
            modifier = Modifier.weight(weight = 1f),
        )
        Text(text = "emitted by", style = MaterialTheme.typography.caption, color = TextMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ActionRow(
    action: EditableAction,
    ordinal: Int,
    selected: Boolean,
    editable: Boolean,
    emitted: Int?,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val issues = ActionIssues.ofAction(action = action, emittedBy = emitted)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = if (selected) PrimaryGlow else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) PrimaryBlue.copy(alpha = SELECTED_BORDER_ALPHA) else Color.Transparent,
                shape = RoundedCornerShape(size = 6.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            Text(
                text = "$ordinal",
                style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                color = TextMuted,
                modifier = Modifier.width(width = 22.dp).padding(top = 2.dp),
            )
            Column(modifier = Modifier.weight(weight = 1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 3.dp),
                ) {
                    Mono(text = action.name.ifBlank { "…" }, color = AccentPurple, bold = true)
                    Mono(text = "(", color = TextMuted)
                    action.argTypes.forEachIndexed { index, type ->
                        if (index > 0) Mono(text = ", ", color = TextMuted)
                        Mono(text = type, color = AccentOrange)
                    }
                    Mono(text = ")", color = TextMuted)
                }
                if (action.purpose.isNotBlank()) {
                    Text(
                        text = action.purpose,
                        style = MaterialTheme.typography.caption,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
                issues.firstOrNull { issue -> issue.level != IssueLevel.NOTE }
                    ?.let { issue -> IssueNote(issue = issue) }
            }
            EmittedTag(count = emitted)
            if (selected && editable) {
                TinyButton(text = "×", onClick = onRemove)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Mono(text: String, color: Color, bold: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = color,
        // The signature wraps between its parts, never inside one: a type split across two lines is the
        // `inte` / `ger` defect the prototype's measurement pass caught.
        maxLines = 1,
        softWrap = false,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun IssueNote(issue: SchemaIssue) {
    Text(
        text = issue.message,
        style = MaterialTheme.typography.caption,
        color = if (issue.level == IssueLevel.ERROR) AccentRed else AccentOrange,
        modifier = Modifier.padding(start = 2.dp, top = 1.dp),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun EmittedTag(count: Int?) {
    val emitted = count ?: return
    Text(
        text = when (emitted) {
            0 -> "unused"
            1 -> "1 rule"
            else -> "$emitted rules"
        },
        style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
        color = if (emitted == 0) AccentOrange else TextMuted,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(top = 2.dp),
    )
}

private fun uniqueName(taken: List<String>, base: String): String {
    var name = base
    var index = 2
    while (name in taken) {
        name = "$base$index"
        index++
    }
    return name
}

private const val SELECTED_BORDER_ALPHA: Float = 0.4f

/** Guards on the canvas's structural gestures. */
internal object ActionCanvasGuards {

    /**
     * Why [name] may not be removed, or null when it may.
     *
     * An action a loaded rule emits is not deleted silently: a rule emitting an undeclared action does
     * not compile, so the delete would break the entry. Same rule as the schema canvas's, and as the
     * Builder's.
     */
    fun blockedRemoval(name: String, emittedBy: Int): String? = when {
        emittedBy > 0 -> "Not removed: $name is emitted by " +
            (if (emittedBy == 1) "1 rule" else "$emittedBy rules") +
            ". A rule emitting an undeclared action does not compile."

        else -> null
    }
}
