package ui.schema.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import ruleengine.core.domain.dto.field.isStructure
import ui.AccentCyan
import ui.AccentOrange
import ui.AccentPurple
import ui.AccentRed
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextMuted
import ui.TextSecondary
import ui.components.TinyButton
import ui.schema.FieldTemplates
import ui.schema.IssueLevel
import ui.schema.SchemaIssues
import ui.schema.model.EditableField
import ui.schema.model.SchemaEditorState
import ui.schema.removeAtPath
import ui.schema.updateAtPath

/**
 * The field schema as an outline: one line per field, nested members on a bracket rail.
 *
 * What this replaces was a table in name only. `FieldSchemaTable` drew a `Path / Alias / Type /
 * Normalizers / Operators` header and then, under it, one bordered **card** per field holding three text
 * fields, a dropdown and two buttons on its first line, then a Format line, a Normalizers line of six
 * chips and an Operators line of up to seven. So the headers described columns that did not exist below
 * them, a field cost three to five rows of height, and — because every cell was a live text box — the
 * schema could only be edited, never read.
 *
 * Here the row is read and the Inspector writes. Twelve fields is twelve lines.
 *
 * The whole row is the click target. The 36 dp `ⓘ` button it replaces existed for a stated reason —
 * "every cell is a text field, so a row-wide target would fight the editing under it" — and that reason
 * is gone with the text fields.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun SchemaCanvas(
    state: SchemaEditorState,
    onStateChange: (SchemaEditorState) -> Unit,
    modifier: Modifier = Modifier,
    /** The dotted path the Inspector is on, highlighted here. */
    selectedPath: String? = null,
    onSelectPath: (String) -> Unit = {},
    /** How many loaded rules read each field, by dotted path. Empty when nothing is parsed. */
    readBy: Map<String, Int> = emptyMap(),
    /** Where a refused gesture explains itself — a delete that would break a rule. */
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

        SectionHeader(
            title = "FIELDS",
            hint = "declaration order is the file's order, nothing else",
            trailing = {
                if (editable) {
                    AddFieldRow(state = state, onStateChange = onStateChange, onSelectPath = onSelectPath)
                }
            },
        )
        ColumnLegend()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f)
                .verticalScroll(state = rememberScrollState()),
        ) {
            state.fields.forEachIndexed { index, field ->
                FieldRow(
                    field = field,
                    path = field.path,
                    ordinal = "${index + 1}",
                    depth = 0,
                    state = state,
                    editable = editable,
                    selectedPath = selectedPath,
                    onSelectPath = onSelectPath,
                    readBy = readBy,
                    onStateChange = onStateChange,
                    onMessage = onMessage,
                )
            }
            if (state.fields.isEmpty()) {
                Text(
                    text = "(no fields yet — a rule can read nothing until one is declared)",
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 30.dp, top = 6.dp),
                )
            }
        }
    }
}

/**
 * One field, and — when it is a structure — its members on a rail beneath it.
 *
 * Recursive, so nesting is unbounded, and the rail's colour changes with depth so the level is read at a
 * glance rather than counted.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun FieldRow(
    field: EditableField,
    path: String,
    ordinal: String,
    depth: Int,
    state: SchemaEditorState,
    editable: Boolean,
    selectedPath: String?,
    onSelectPath: (String) -> Unit,
    readBy: Map<String, Int>,
    onStateChange: (SchemaEditorState) -> Unit,
    onMessage: (String) -> Unit,
) {
    val selected = path == selectedPath
    val issues = SchemaIssues.ofField(path = path, field = field, readBy = readBy[path])
    val uses = readBy[path]

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
            .clickable { onSelectPath(path) }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        FieldLine(
            field = field,
            ordinal = if (depth > 0) "·" else ordinal,
            // Notes are not row issues: "no rule reads this" is already said by the `unread` tag on the
            // same line, and repeating it in warning colour made a third of the rows look wrong.
            issue = issues.firstOrNull { found -> found.level != IssueLevel.NOTE },
            uses = uses,
            showActions = selected && editable,
        ) {
            RowActions(
                field = field,
                path = path,
                readBy = uses ?: 0,
                state = state,
                onStateChange = onStateChange,
                onSelectPath = onSelectPath,
                onMessage = onMessage,
            )
        }
    }

    if (field.type.isStructure) {
        MemberRail(
            field = field,
            path = path,
            depth = depth,
            state = state,
            editable = editable,
            selectedPath = selectedPath,
            onSelectPath = onSelectPath,
            readBy = readBy,
            onStateChange = onStateChange,
            onMessage = onMessage,
        )
    }
}

/** The line itself: the ordinal, the tokens, the issue under them, and the usage tag. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun FieldLine(
    field: EditableField,
    ordinal: String,
    issue: ui.schema.SchemaIssue?,
    uses: Int?,
    showActions: Boolean,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = ordinal,
            style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
            color = TextMuted,
            modifier = Modifier.width(width = 22.dp).padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(weight = 1f)) {
            FieldTokens(field = field)
            issue?.let { found -> IssueNote(issue = found) }
        }
        UsageTag(uses = uses)
        if (showActions) {
            actions()
        }
    }
}

/**
 * A structure's members, drawn as a bracket rail.
 *
 * Reuses the Builder's group geometry exactly, including the part that is easy to get wrong:
 * `IntrinsicSize.Min` is what gives the rail a height to fill. Without it the `Box` has no intrinsic
 * height of its own and collapses to nothing, leaving the group with no bracket at all.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun MemberRail(
    field: EditableField,
    path: String,
    depth: Int,
    state: SchemaEditorState,
    editable: Boolean,
    selectedPath: String?,
    onSelectPath: (String) -> Unit,
    readBy: Map<String, Int>,
    onStateChange: (SchemaEditorState) -> Unit,
    onMessage: (String) -> Unit,
) {
    val railColour = when (depth % RAIL_COLOURS) {
        0 -> PrimaryBlue
        1 -> AccentCyan
        else -> AccentPurple
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(intrinsicSize = IntrinsicSize.Min)
            .padding(start = 22.dp, top = 1.dp, bottom = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .width(width = 2.dp)
                .fillMaxHeight()
                .clip(shape = RoundedCornerShape(size = 1.dp))
                .background(color = railColour.copy(alpha = RAIL_ALPHA)),
        )
        Column(
            modifier = Modifier
                .weight(weight = 1f)
                .background(color = railColour.copy(alpha = RAIL_FILL_ALPHA))
                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                text = "${field.path.uppercase()} MEMBERS",
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = railColour,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            field.fields.forEachIndexed { index, member ->
                FieldRow(
                    field = member,
                    path = "$path.${member.path}",
                    ordinal = "${index + 1}",
                    depth = depth + 1,
                    state = state,
                    editable = editable,
                    selectedPath = selectedPath,
                    onSelectPath = onSelectPath,
                    readBy = readBy,
                    onStateChange = onStateChange,
                    onMessage = onMessage,
                )
            }
            if (field.fields.isEmpty()) {
                Text(
                    text = "(no members — a structure with nothing in it cannot be navigated into)",
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary,
                )
            }
        }
    }
}

/** The selected row's own controls, which is the only time they take any space. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RowActions(
    field: EditableField,
    path: String,
    readBy: Int,
    state: SchemaEditorState,
    onStateChange: (SchemaEditorState) -> Unit,
    onSelectPath: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 3.dp)) {
        if (field.type.isStructure) {
            TinyButton(
                text = "+",
                onClick = {
                    val name = uniqueName(taken = field.fields.map { member -> member.path }, base = "member")
                    onStateChange(
                        state.copy(
                            fields = state.fields.updateAtPath(dotted = path) { host ->
                                host.copy(fields = host.fields + EditableField(path = name))
                            },
                        ),
                    )
                    onSelectPath("$path.$name")
                },
            )
        }
        TinyButton(
            text = "×",
            onClick = {
                val blocked = SchemaCanvasGuards.blockedRemoval(path = path, readBy = readBy)
                if (blocked != null) {
                    onMessage(blocked)
                } else {
                    onStateChange(state.copy(fields = state.fields.removeAtPath(dotted = path)))
                }
            },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun IssueNote(issue: ui.schema.SchemaIssue) {
    Text(
        text = issue.message,
        style = MaterialTheme.typography.caption,
        color = if (issue.level == IssueLevel.ERROR) AccentRed else AccentOrange,
        modifier = Modifier.padding(start = 2.dp, top = 1.dp),
    )
}

/** How many rules read this field, or `unread`, which is the case worth noticing. */
@Suppress("FunctionNaming")
@Composable
private fun UsageTag(uses: Int?) {
    val count = uses ?: return
    Text(
        text = if (count == 0) "unread" else if (count == 1) "1 rule" else "$count rules",
        style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
        color = if (count == 0) AccentOrange else TextMuted,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
private fun AddFieldRow(
    state: SchemaEditorState,
    onStateChange: (SchemaEditorState) -> Unit,
    onSelectPath: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        FieldTemplates.forEach { (label, template) ->
            TinyButton(
                text = "+ ${label.substringBefore(delimiter = " ").lowercase()}",
                onClick = {
                    val name = uniqueName(
                        taken = state.fields.map { field -> field.path },
                        base = template.path.ifBlank { "field" },
                    )
                    val added = template.copy(path = name)
                    onStateChange(state.copy(fields = state.fields + added))
                    onSelectPath(name)
                },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SectionHeader(title: String, hint: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = PrimaryBlue,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.weight(weight = 1f),
        )
        trailing()
    }
}

/** What the columns of the line mean, since the line is not a table. */
@Suppress("FunctionNaming")
@Composable
private fun ColumnLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "path · type · what it carries",
            style = MaterialTheme.typography.caption,
            color = TextMuted,
            modifier = Modifier.weight(weight = 1f),
        )
        Text(text = "read by", style = MaterialTheme.typography.caption, color = TextMuted)
    }
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

/** Colours the rail cycles through, one per nesting level. */
private const val RAIL_COLOURS: Int = 3
private const val RAIL_ALPHA: Float = 0.55f
private const val RAIL_FILL_ALPHA: Float = 0.05f
private const val SELECTED_BORDER_ALPHA: Float = 0.4f

/** Guards on the canvas's structural gestures. */
internal object SchemaCanvasGuards {

    /**
     * Why [path] may not be removed, or null when it may.
     *
     * A field a loaded rule reads is not deleted silently: the rules are on disk, the delete would stop
     * the entry compiling, and the refusal is where that gets taught. Same rule as the Builder's
     * `blockedRemoval`, and the same reason — a gesture that springs back without saying why is
     * indistinguishable from a broken one.
     */
    fun blockedRemoval(path: String, readBy: Int): String? = when {
        readBy > 0 -> "Not removed: $path is read by " +
            (if (readBy == 1) "1 rule" else "$readBy rules") +
            ". Change those rules first, or the entry stops compiling."

        else -> null
    }
}
