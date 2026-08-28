package ui.manifest

import ui.manifest.model.ManifestEditorState
import ui.schema.IssueLevel
import ui.schema.SchemaIssue

/**
 * What would stop this manifest from loading, plus the softer omissions.
 *
 * Lifted out of `ManifestEditorPanel`, where it produced plain strings for one bullet list, so the dock
 * and the panel can share one set of verdicts — and so each verdict can name the **entry** it is about
 * and be clicked rather than read.
 *
 * Duplicate entry ids are the one that matters most: the engine rejects the manifest outright, and the
 * id is otherwise invisible enough that two entries can end up sharing one by accident.
 *
 * The scope check needs a schema, so it only runs for the active entry — [fieldTypes] describes the
 * schema *that* entry loaded, and a sibling may name another one entirely.
 */
object ManifestIssues {

    fun of(
        state: ManifestEditorState,
        activeEntryId: String?,
        fieldTypes: Map<String, String>?,
        /** Whether the editor holds the file a path names. Null skips the check entirely. */
        isLoaded: ((String) -> Boolean)? = null,
    ): List<SchemaIssue> = buildList {
        if (state.name.isBlank()) {
            add(element = SchemaIssue(level = IssueLevel.WARNING, path = "", message = "The manifest has no name."))
        }
        if (state.entries.isEmpty()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.ERROR,
                    path = "",
                    message = "No entry defined, so there is nothing to run.",
                ),
            )
            return@buildList
        }

        state.entries
            .groupBy { entry -> entry.id }
            .filter { (id, entries) -> id.isNotBlank() && entries.size > 1 }
            .forEach { (id, _) ->
                add(
                    element = SchemaIssue(
                        level = IssueLevel.ERROR,
                        path = id,
                        message = "Entry id \"$id\" is used more than once — the engine refuses the manifest.",
                    ),
                )
            }

        state.entries.forEachIndexed { index, entry ->
            val label = entry.id.ifBlank { "entry ${index + 1}" }
            if (entry.id.isBlank()) {
                add(element = SchemaIssue(level = IssueLevel.ERROR, path = "", message = "$label has no id."))
            }
            addAll(elements = fileIssues(entry = entry, label = label, isLoaded = isLoaded))
            if (entry.rulePaths.isEmpty()) {
                add(
                    element = SchemaIssue(
                        level = IssueLevel.ERROR,
                        path = entry.id,
                        message = "$label: no rule files configured, so it has nothing to evaluate.",
                    ),
                )
            }
            if (entry.id == activeEntryId) {
                scopeIssue(scope = entry.scope, fieldTypes = fieldTypes)?.let { issue ->
                    add(element = SchemaIssue(level = IssueLevel.ERROR, path = entry.id, message = "$label: $issue"))
                }
            }
        }
    }

    private fun fileIssues(
        entry: ui.manifest.model.EditableManifestEntry,
        label: String,
        isLoaded: ((String) -> Boolean)?,
    ): List<SchemaIssue> = buildList {
        if (entry.schemaPath.isBlank()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.ERROR,
                    path = entry.id,
                    message = "$label: field schema file not set.",
                ),
            )
        }
        if (entry.actionsPath.isBlank()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.ERROR,
                    path = entry.id,
                    message = "$label: action schema file not set.",
                ),
            )
        }
        val check = isLoaded ?: return@buildList
        entry.rulePaths.filterNot { path -> check(path) }.forEach { path ->
            // "Not loaded", not "missing": a project that has never been saved has no filesystem to
            // check against, so claiming the file is absent would be a verdict the editor cannot make.
            add(
                element = SchemaIssue(
                    level = IssueLevel.WARNING,
                    path = entry.id,
                    message = "$label: $path is not loaded.",
                ),
            )
        }
    }
}
