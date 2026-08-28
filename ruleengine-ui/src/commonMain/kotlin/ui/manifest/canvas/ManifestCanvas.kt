package ui.manifest.canvas

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
import ui.AccentCyan
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.PrimaryBlue
import ui.PrimaryBlueLight
import ui.PrimaryGlow
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.components.TinyButton
import ui.manifest.RuleFileFlow
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.manifest.scopeIssue

/**
 * The manifest as an outline: each entry's properties on a rail, then its rule files in run order.
 *
 * What this replaces was a scrolling column of Material text fields with floating labels — a component
 * family that appears nowhere else in the app — inside bordered cards, one per entry. Eight fields for a
 * file with five keys.
 *
 * Every line here is a row like any other, and the two file paths are **links**: the manifest is the one
 * file whose whole job is to point at the other three, so pointing back is what it should do.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun ManifestCanvas(
    state: ManifestEditorState,
    modifier: Modifier = Modifier,
    /** The entry the project saves against, marked here. */
    activeEntryId: String? = null,
    selected: Boolean = false,
    onSelectManifest: () -> Unit = {},
    onSelectEntry: (String) -> Unit = {},
    /** Opens the Schema or Actions area — the paths are navigation. */
    onOpenSchema: () -> Unit = {},
    onOpenActions: () -> Unit = {},
    /**
     * Whether the editor currently holds the file that path names.
     *
     * "Loaded", not "on disk": the editor knows its working copy and the sample it was given, and a
     * verdict about the filesystem is one it cannot make for a project that has never been saved.
     */
    exists: (String) -> Boolean = { true },
    /**
     * What each rule file publishes and reads, by manifest-relative path.
     *
     * Empty when the rules have not been parsed. A read with no earlier producer is drawn in the warning
     * colour — the entry's one genuine warning, and the reason these rows are reorderable at all.
     */
    variableFlow: Map<String, RuleFileFlow> = emptyMap(),
    /**
     * Each top-level field of the loaded schema and its type, lowercased.
     *
     * The whole map, not just the collections: `scopeIssue` distinguishes "not a field at all" from
     * "is text, not a collection", and it can only tell them apart if it can see the non-collections.
     * Null means no schema is loaded, which is no verdict rather than a complaint.
     */
    fieldTypes: Map<String, String>? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(size = 6.dp))
                .background(color = if (selected) PrimaryGlow else Color.Transparent)
                .clickable(onClick = onSelectManifest)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = state.name.ifBlank { "(unnamed project)" },
                style = MaterialTheme.typography.h6.copy(fontFamily = FontFamily.Monospace),
                color = if (state.name.isBlank()) AccentOrange else TextPrimary,
            )
            Text(
                text = "which schema, which actions, which rule files — and in what order they run",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }

        state.entries.forEachIndexed { index, entry ->
            EntrySection(
                entry = entry,
                ordinal = index + 1,
                isActive = entry.id == activeEntryId,
                fieldTypes = fieldTypes,
                onSelect = { onSelectEntry(entry.id) },
                onOpenSchema = onOpenSchema,
                onOpenActions = onOpenActions,
                exists = exists,
                // Only the active entry's. The loaded rules are that entry's rules, and a sibling can
                // list the same files in another order — which is precisely what changes the answer.
                variableFlow = if (entry.id == activeEntryId) variableFlow else emptyMap(),
            )
        }
        if (state.entries.isEmpty()) {
            Text(
                text = "(no entries — nothing says what to run)",
                style = MaterialTheme.typography.caption,
                color = AccentOrange,
                modifier = Modifier.padding(start = 26.dp, top = 8.dp),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun EntryHeader(
    entry: EditableManifestEntry,
    ordinal: Int,
    isActive: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "ENTRY $ordinal",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = PrimaryBlue,
        )
        Text(
            text = entry.id.ifBlank { "(no id)" } + if (isActive) "" else " — not the entry being edited",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        if (!isActive) {
            TinyButton(text = "edit this entry", onClick = onSelect)
        }
    }
}

/**
 * One entry: its four properties inside a rail, then its rule files.
 *
 * The rail rather than four separately highlighted rows, because they are all one selection — the entry.
 * Four highlighted rows would claim to be four.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun EntrySection(
    entry: EditableManifestEntry,
    ordinal: Int,
    isActive: Boolean,
    fieldTypes: Map<String, String>?,
    onSelect: () -> Unit,
    onOpenSchema: () -> Unit,
    onOpenActions: () -> Unit,
    exists: (String) -> Boolean,
    variableFlow: Map<String, RuleFileFlow>,
) {
    EntryHeader(entry = entry, ordinal = ordinal, isActive = isActive, onSelect = onSelect)

    PropertyRail(
        entry = entry,
        isActive = isActive,
        fieldTypes = fieldTypes,
        onSelect = onSelect,
        onOpenSchema = onOpenSchema,
        onOpenActions = onOpenActions,
        exists = exists,
    )

    RuleFileList(entry = entry, exists = exists, variableFlow = variableFlow)
}

@Suppress("FunctionNaming")
@Composable
private fun RuleFileList(
    entry: EditableManifestEntry,
    exists: (String) -> Boolean,
    variableFlow: Map<String, RuleFileFlow>,
) {
    RuleListHeader()
    entry.rulePaths.forEachIndexed { index, path ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                color = TextMuted,
                modifier = Modifier.width(width = 18.dp),
            )
            Text(
                text = path,
                style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
                color = if (exists(path)) AccentCyan else AccentOrange,
                maxLines = 1,
                softWrap = false,
            )
            if (!exists(path)) {
                Text(
                    text = "not loaded ⚠",
                    style = MaterialTheme.typography.caption,
                    color = AccentOrange,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            VariableChips(flow = variableFlow[path])
        }
        variableFlow[path]?.reads?.filterNot { read -> read.resolved }?.forEach { read ->
            Text(
                text = "reads \$${read.name} before any file publishes it — null on every run",
                style = MaterialTheme.typography.caption,
                color = AccentOrange,
                modifier = Modifier.padding(start = 36.dp),
            )
        }
    }
    if (entry.rulePaths.isEmpty()) {
        Text(
            text = "(no rule files — this entry has nothing to evaluate)",
            style = MaterialTheme.typography.caption,
            color = AccentOrange,
            modifier = Modifier.padding(start = 30.dp),
        )
    }
    Text(
        text = "Reorder and add in the Inspector.",
        style = MaterialTheme.typography.caption,
        color = TextMuted,
        modifier = Modifier.padding(start = 30.dp, top = 3.dp),
    )
}

/**
 * The entry's four properties, on one rail.
 *
 * The rail rather than four separately highlighted rows, because they are all one selection — the entry.
 * Four highlighted rows would claim to be four.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun PropertyRail(
    entry: EditableManifestEntry,
    isActive: Boolean,
    fieldTypes: Map<String, String>?,
    onSelect: () -> Unit,
    onOpenSchema: () -> Unit,
    onOpenActions: () -> Unit,
    exists: (String) -> Boolean,
) {
    // IntrinsicSize.Min is what gives the rail a height to fill; without it the Box collapses and the
    // entry loses its bracket. Same requirement as the Builder's condition groups.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(intrinsicSize = IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(width = 2.dp)
                .fillMaxHeight()
                .clip(shape = RoundedCornerShape(size = 1.dp))
                .background(
                    color = if (isActive) PrimaryBlue else PrimaryBlue.copy(alpha = RAIL_IDLE_ALPHA),
                ),
        )
        Column(
            modifier = Modifier
                .weight(weight = 1f)
                .background(
                    color = if (isActive) PrimaryBlue.copy(alpha = RAIL_FILL_ALPHA) else Color.Transparent,
                )
                .clickable(onClick = onSelect)
                .padding(start = 10.dp, top = 3.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            KeyValue(key = "id", value = entry.id.ifBlank { "—" }, colour = AccentCyan) {
                if (isActive) {
                    Text(
                        text = "the save target",
                        style = MaterialTheme.typography.caption,
                        color = PrimaryBlueLight,
                    )
                }
            }
            KeyValue(
                key = "schema",
                value = entry.schemaPath.ifBlank { "not set" },
                colour = if (entry.schemaPath.isBlank()) AccentOrange else AccentCyan,
                onValueClick = onOpenSchema.takeIf { entry.schemaPath.isNotBlank() },
            ) {
                FileNote(path = entry.schemaPath, exists = exists, opens = "schema")
            }
            KeyValue(
                key = "actions",
                value = entry.actionsPath.ifBlank { "not set" },
                colour = if (entry.actionsPath.isBlank()) AccentOrange else AccentCyan,
                onValueClick = onOpenActions.takeIf { entry.actionsPath.isNotBlank() },
            ) {
                FileNote(path = entry.actionsPath, exists = exists, opens = "actions")
            }
            KeyValue(
                key = "scope",
                value = entry.scope.ifBlank { "(none)" },
                colour = if (entry.scope.isBlank()) TextMuted else AccentCyan,
            ) {
                ScopeNote(entry = entry, fieldTypes = fieldTypes)
            }
        }
    }
}

/**
 * What a file publishes and what it reads, on the row.
 *
 * `↑` sets and `↓` reads, and a read that cannot resolve is orange — so the consequence of the order is
 * on the same line as the control that changes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
private fun VariableChips(flow: RuleFileFlow?) {
    val present = flow ?: return
    if (present.sets.isEmpty() && present.reads.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 3.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        present.sets.forEach { name -> VariableChip(text = "↑\$$name", colour = AccentGreen) }
        present.reads.forEach { read ->
            VariableChip(
                text = "↓\$${read.name}",
                colour = if (read.resolved) AccentOrange else AccentRed,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun VariableChip(text: String, colour: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
        color = colour,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = colour.copy(alpha = CHIP_ALPHA))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun RuleListHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "RULES",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = PrimaryBlue,
        )
        Text(
            text = "this order is the run order — a \$variable is visible only to the files after " +
                "the one that sets it",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun KeyValue(
    key: String,
    value: String,
    colour: Color,
    onValueClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextMuted,
            modifier = Modifier.width(width = 58.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
            color = colour,
            maxLines = 1,
            softWrap = false,
            modifier = if (onValueClick == null) {
                Modifier
            } else {
                Modifier
                    .clip(shape = RoundedCornerShape(size = 4.dp))
                    .border(
                        width = 1.dp,
                        color = colour.copy(alpha = LINK_BORDER_ALPHA),
                        shape = RoundedCornerShape(size = 4.dp),
                    )
                    .clickable(onClick = onValueClick)
                    .padding(horizontal = 4.dp)
            },
        )
        trailing()
    }
}

@Suppress("FunctionNaming")
@Composable
private fun FileNote(path: String, exists: (String) -> Boolean, opens: String) {
    if (path.isBlank()) {
        Text(
            text = "the entry cannot load without it",
            style = MaterialTheme.typography.caption,
            color = AccentOrange,
        )
        return
    }
    if (!exists(path)) {
        Text(
            text = "not loaded ⚠",
            style = MaterialTheme.typography.caption,
            color = AccentOrange,
        )
        return
    }
    Text(
        text = "opens the $opens area",
        style = MaterialTheme.typography.caption,
        color = TextMuted,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun ScopeNote(entry: EditableManifestEntry, fieldTypes: Map<String, String>?) {
    val issue = scopeIssue(scope = entry.scope, fieldTypes = fieldTypes)
    Text(
        text = when {
            issue != null -> "$issue ⚠"
            entry.scope.isBlank() -> "the rules run once per document"
            else -> "the rules run once per member"
        },
        style = MaterialTheme.typography.caption,
        color = if (issue != null) AccentOrange else TextMuted,
    )
}

private const val RAIL_IDLE_ALPHA: Float = 0.35f
private const val RAIL_FILL_ALPHA: Float = 0.06f
private const val LINK_BORDER_ALPHA: Float = 0.45f
private const val CHIP_ALPHA: Float = 0.16f
