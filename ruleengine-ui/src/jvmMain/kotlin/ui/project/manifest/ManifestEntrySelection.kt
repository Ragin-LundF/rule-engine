package ui.project.manifest

import ruleengine.manifest.ProjectManifest
import ui.project.model.ProjectSession

/**
 * What the top bar can say about manifest entries, from whichever source actually knows.
 *
 * There are two, and only one of them used to be consulted. [ProjectSession] is authoritative while a
 * project is open, and it is what the picker read; but a sample has no session, so after loading one
 * the picker either vanished or — worse, with a project already open — went on naming the *previous*
 * project's entry. The parsed manifest is the other source, and it is the one that always describes
 * what is on screen.
 */
data class ManifestEntrySelection(
    val entryIds: List<String>,
    val activeEntryId: String,
    /**
     * Whether switching entries and adding one are real operations.
     *
     * False without a session: `ProjectWorkspace.selectEntry` and `addEntry` both return early when
     * there is none, so offering either would be a dead control. The picker becomes a read-only
     * indicator — it still says which entry the workbench is on, which is the part that was missing.
     */
    val editable: Boolean,
)

/**
 * Resolves what the picker shows, or null when there is no manifest to describe.
 *
 * The session wins where it exists, because it holds entries the parsed manifest may not have caught
 * up with — a freshly added entry lives on the session until the buffers are regenerated from it.
 */
internal fun manifestEntrySelection(
    session: ProjectSession?,
    parsedManifest: ProjectManifest?,
    selectedEntryId: String?,
): ManifestEntrySelection? {
    if (session != null) {
        return ManifestEntrySelection(
            entryIds = session.entries.map { entry -> entry.id },
            activeEntryId = session.activeEntryId,
            editable = true,
        )
    }
    val entryIds = parsedManifest?.entries?.map { entry -> entry.id }.orEmpty()
    if (entryIds.isEmpty()) return null
    return ManifestEntrySelection(
        entryIds = entryIds,
        // Mirrors `RuleEditorState.activeScope`, which falls back to the first entry for the same
        // reason: a selection that no longer matches any entry is stale, not a reason to show nothing.
        activeEntryId = entryIds.firstOrNull { id -> id == selectedEntryId } ?: entryIds.first(),
        editable = false,
    )
}
