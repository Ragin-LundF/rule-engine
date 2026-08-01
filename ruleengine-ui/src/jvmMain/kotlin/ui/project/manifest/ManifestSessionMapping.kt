package ui.project.manifest

import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.project.model.ProjectEntry
import ui.project.model.ProjectSession

/**
 * Translates between the session — the project as the workspace holds it — and the manifest editor's
 * own state.
 *
 * The session is the single source of truth: the manifest text and the parsed model shown elsewhere
 * are produced from it, and the Manifest area's edits come back through here rather than being
 * written into a second, competing copy that the saver would then discard.
 */
fun ProjectSession.toEditorState(): ManifestEditorState {
    return ManifestEditorState(
        name = manifestName ?: displayName,
        entries = entries.map { entry ->
            EditableManifestEntry(
                id = entry.id,
                schemaPath = entry.schemaLink.orEmpty(),
                actionsPath = entry.actionsLink.orEmpty(),
                rulePaths = entry.ruleFiles,
            )
        },
    )
}

fun ManifestEditorState.toProjectEntries(): List<ProjectEntry> {
    return entries.map { entry ->
        ProjectEntry(
            id = entry.id,
            schemaLink = entry.schemaPath.takeIf { it.isNotBlank() },
            actionsLink = entry.actionsPath.takeIf { it.isNotBlank() },
            ruleFiles = entry.rulePaths.filter { it.isNotBlank() },
        )
    }
}
