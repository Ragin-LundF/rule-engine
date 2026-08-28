package ui.workbench.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.input.ChooseButton
import ui.components.input.OrderedListEditor
import ui.components.input.PathField
import ui.components.input.ReasonedChipRow
import ui.components.input.model.OrderedListOption
import ui.components.input.model.ReasonedChip
import ui.manifest.SCOPE_NONE
import ui.manifest.collectionNames
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.manifest.model.ManifestPathKind
import ui.manifest.scopeIssue

/**
 * The editing surface for the manifest, and for the entry the project saves against.
 *
 * The rule files are an [OrderedListEditor] because **their order is the run order** — `ManifestEntry`
 * says so — and a `$variable` is visible only to the files listed after the one that sets it. The
 * editor this replaces offered edit-text and remove and no way to reorder at all: changing the run
 * order meant retyping the paths.
 *
 * The one thing in this file that changes behaviour was the one thing that could not be edited as what
 * it is.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun ManifestInspector(
    manifest: ManifestEditorState,
    onManifestChange: (ManifestEditorState) -> Unit,
    modifier: Modifier = Modifier,
    activeEntryId: String? = null,
    /**
     * Each top-level field of the loaded schema and its type. Null means no schema, which is why the
     * picker then offers only what the entry already carries — there is nothing to check a name against.
     */
    fieldTypes: Map<String, String>? = null,
    /**
     * Opens the platform file dialog and returns the chosen file **as a manifest-relative path**, or
     * null if the dialog was cancelled. Null hides the `Choose…` buttons entirely.
     *
     * Relative, not absolute, and relativized by the caller because only the caller knows where the
     * manifest lives — see [choosePathDisabledReason] for what happens when nothing does.
     */
    choosePath: ((ManifestPathKind) -> String?)? = null,
    /** Why the dialog cannot be used — an unsaved project has no location to be relative to. */
    choosePathDisabledReason: String? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth >= WIDE_FROM
        val editable = !manifest.isReadOnly
        val entryIndex = manifest.entries.indexOfFirst { entry -> entry.id == activeEntryId }
            .takeIf { index -> index >= 0 }
            ?: 0
        val entry = manifest.entries.getOrNull(index = entryIndex)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(state = rememberScrollState())
                .padding(horizontal = if (wide) 18.dp else 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            InspectorHeading(title = manifest.name.ifBlank { "manifest" }, kind = "Manifest")

            InspectorGroup(title = "Identity")
            InspectorTextField(
                label = "name",
                value = manifest.name,
                placeholder = "the project name",
                enabled = editable,
                wide = wide,
                onValueChange = { text -> onManifestChange(manifest.copy(name = text)) },
            )

            InspectorGroup(
                title = "Entries",
                note = "${manifest.entries.size} " + if (manifest.entries.size == 1) "entry" else "entries",
            )
            if (entry == null) {
                InspectorNote(text = "No entry defined, so there is nothing to run.", warning = true)
                return@BoxWithConstraints
            }

            EntrySection(
                entry = entry,
                editable = editable,
                wide = wide,
                fieldTypes = fieldTypes,
                choosePath = choosePath,
                choosePathDisabledReason = choosePathDisabledReason,
                onEntryChange = { updated ->
                    onManifestChange(
                        manifest.copy(
                            entries = manifest.entries.toMutableList()
                                .also { list -> list[entryIndex] = updated },
                        ),
                    )
                },
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun EntrySection(
    entry: EditableManifestEntry,
    editable: Boolean,
    wide: Boolean,
    fieldTypes: Map<String, String>?,
    choosePath: ((ManifestPathKind) -> String?)?,
    choosePathDisabledReason: String?,
    onEntryChange: (EditableManifestEntry) -> Unit,
) {
    InspectorTextField(
        label = "entry id",
        value = entry.id,
        placeholder = "default",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onEntryChange(entry.copy(id = text)) },
    )
    InspectorNote(
        text = "The id the project saves against. A duplicate makes the engine refuse the whole manifest.",
    )

    SchemaPathFields(
        entry = entry,
        editable = editable,
        wide = wide,
        choosePath = choosePath,
        choosePathDisabledReason = choosePathDisabledReason,
        onEntryChange = onEntryChange,
    )
    RuleFilesSection(
        entry = entry,
        editable = editable,
        choosePath = choosePath,
        choosePathDisabledReason = choosePathDisabledReason,
        onEntryChange = onEntryChange,
    )

    ScopeSection(entry = entry, editable = editable, fieldTypes = fieldTypes, onEntryChange = onEntryChange)
}

/** The entry's two schema files: typed, or chosen from a dialog. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SchemaPathFields(
    entry: EditableManifestEntry,
    editable: Boolean,
    wide: Boolean,
    choosePath: ((ManifestPathKind) -> String?)?,
    choosePathDisabledReason: String?,
    onEntryChange: (EditableManifestEntry) -> Unit,
) {
    InspectorGroup(title = "Files")
    PathField(
        label = "field schema",
        value = entry.schemaPath,
        placeholder = "schema.yaml",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onEntryChange(entry.copy(schemaPath = text)) },
        onChoose = choosePath?.let { pick ->
            { pick(ManifestPathKind.SCHEMA)?.let { path -> onEntryChange(entry.copy(schemaPath = path)) } }
        },
        chooseDisabledReason = choosePathDisabledReason,
    )
    PathField(
        label = "action schema",
        value = entry.actionsPath,
        placeholder = "actions.yaml",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onEntryChange(entry.copy(actionsPath = text)) },
        onChoose = choosePath?.let { pick ->
            { pick(ManifestPathKind.ACTIONS)?.let { path -> onEntryChange(entry.copy(actionsPath = path)) } }
        },
        chooseDisabledReason = choosePathDisabledReason,
    )
    InspectorNote(text = "Relative to the manifest file.")
}

/**
 * The rule files, as an order.
 *
 * Each row's path is editable text with a picker beside it, because a manifest path is often one that
 * does not exist yet — the saver creates `rules/` — and no dialog can offer a file nobody has written.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RuleFilesSection(
    entry: EditableManifestEntry,
    editable: Boolean,
    choosePath: ((ManifestPathKind) -> String?)?,
    choosePathDisabledReason: String?,
    onEntryChange: (EditableManifestEntry) -> Unit,
) {
    InspectorGroup(title = "Rule files", note = "in run order")
    OrderedListEditor(
        items = entry.rulePaths,
        // Nothing to offer: a rule file is a path, not a value from a known set. Its text is typed on
        // the row, or chosen with the button beside it.
        options = emptyList<OrderedListOption>(),
        onMove = { from, to -> onEntryChange(entry.copy(rulePaths = entry.rulePaths.moved(from, to))) },
        onRemove = { index ->
            onEntryChange(entry.copy(rulePaths = entry.rulePaths.filterIndexed { at, _ -> at != index }))
        },
        onAdd = { },
        emptyText = "No rule files, so this entry has nothing to evaluate.",
        enabled = editable,
        allowDuplicates = true,
        onEdit = { index, text ->
            onEntryChange(entry.copy(rulePaths = entry.rulePaths.replacedAt(index = index, value = text)))
        },
        rowAction = { index ->
            ChooseButton(
                onChoose = choosePath?.let { pick ->
                    {
                        pick(ManifestPathKind.RULE)?.let { path ->
                            onEntryChange(entry.copy(rulePaths = entry.rulePaths.replacedAt(index, path)))
                        }
                    }
                },
                enabled = editable,
                reason = choosePathDisabledReason,
            )
        },
    )
    if (editable) {
        InspectorAddButton(
            label = "+ rule file",
            // The picker when there is one, and a placeholder path when there is not — the saver creates
            // `rules/`, so a path that does not exist yet is a legitimate thing to write.
            onClick = {
                val chosen = choosePath?.takeIf { choosePathDisabledReason == null }
                    ?.invoke(ManifestPathKind.RULE)
                onEntryChange(entry.copy(rulePaths = entry.rulePaths + (chosen ?: "rules/new-rules.rule")))
            },
        )
    }
    if (choosePathDisabledReason != null) {
        InspectorNote(text = choosePathDisabledReason)
    }
    InspectorNote(
        text = "This order is the run order, and a \$variable is visible only to the files after the " +
            "one that sets it — so these arrows are the only control in the app that can create, or " +
            "fix, a read that never resolves.",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
private fun ScopeSection(
    entry: EditableManifestEntry,
    editable: Boolean,
    fieldTypes: Map<String, String>?,
    onEntryChange: (EditableManifestEntry) -> Unit,
) {
    InspectorGroup(title = "Scope", note = "optional")

    // The declared collections, plus an off-list value the entry already carries. That value is
    // deliberately offered as itself rather than swapped for something legal: it is in the file, the
    // engine will refuse it, and the reader has to be able to see and correct it.
    val declared = collectionNames(fieldTypes = fieldTypes)
    val options = buildList {
        add(element = SCOPE_NONE)
        addAll(elements = declared)
        if (entry.scope.isNotBlank() && entry.scope !in declared) add(element = entry.scope)
    }
    val issue = scopeIssue(scope = entry.scope, fieldTypes = fieldTypes)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        options.forEach { option ->
            val isNone = option == SCOPE_NONE
            val value = if (isNone) "" else option
            val offList = !isNone && option !in declared
            ReasonedChipRow(
                chips = listOf(
                    ReasonedChip(
                        value = option,
                        selected = value == entry.scope,
                        blockedReason = if (offList) {
                            scopeIssue(scope = option, fieldTypes = fieldTypes)
                        } else {
                            null
                        },
                    ),
                ),
                enabled = editable,
                onToggle = { onEntryChange(entry.copy(scope = value)) },
            )
        }
    }

    InspectorNote(
        text = when {
            issue != null -> "$issue — the engine refuses the manifest at load time."
            entry.scope.isBlank() -> "The rules run once for the whole document."
            else -> "Every rule runs once per member of ${entry.scope}, resolving a rule's paths " +
                "against the member first and the document second."
        },
        warning = issue != null,
    )
    if (fieldTypes == null) {
        InspectorNote(
            text = "No schema is loaded, so a scope cannot be checked against anything — the value the " +
                "manifest already carries is offered as itself.",
        )
    }
}

/** [this] with [index] set to [value]. An out-of-range index leaves the list alone. */
private fun List<String>.replacedAt(index: Int, value: String): List<String> {
    if (index !in indices) return this
    return toMutableList().also { copy -> copy[index] = value }
}

/** [this] with the item at [from] moved to [to]. Out-of-range indices leave the list alone. */
private fun List<String>.moved(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices || from == to) return this
    val copy = toMutableList()
    copy.add(index = to, element = copy.removeAt(index = from))
    return copy
}

private val WIDE_FROM = 480.dp
