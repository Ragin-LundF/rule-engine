package ui.project.model

import ui.project.model.dialog.MissingProjectFile
import java.nio.file.Path

/**
 * The on-disk identity of the project currently open.
 *
 * This is what the editor state has never had: buffers alone cannot say where they came from, which
 * is why a loaded schema could never be saved back and why loading a second manifest left the first
 * one's content on screen. A null session means "scratch" — everything is in memory and the first
 * save asks where to put it.
 *
 * The manifest's entries are all held, not just the one being edited, so that saving rewrites the
 * whole manifest rather than collapsing it to whichever entry happened to be open. [activeEntryId]
 * says which one the buffers currently hold.
 *
 * Immutable; the UI holds it in a `mutableStateOf` and replaces it on every load and save.
 */
data class ProjectSession(
    val root: Path,
    val manifestFileName: String,
    val entries: List<ProjectEntry>,
    val activeEntryId: String,
    /** The manifest's `name:`, kept so a rewrite does not invent one from the folder name. */
    val manifestName: String? = null,
    val missingFiles: List<MissingProjectFile> = emptyList(),
) {
    val manifestPath: Path get() = root.resolve(manifestFileName)

    /** Shown in the title and status bars — the folder name, which is how users name projects. */
    val displayName: String get() = root.fileName?.toString() ?: root.toString()

    /**
     * The entry the editor buffers belong to.
     *
     * Falls back to the first entry rather than throwing: an id that no longer matches means a
     * rename raced the selection, and showing the first entry beats crashing the workbench.
     */
    val active: ProjectEntry get() = entries.firstOrNull { it.id == activeEntryId } ?: entries.first()

    // Views onto the active entry, so everything that edits "the" schema, actions or rules keeps
    // reading one flat surface and never has to know which entry it is looking at.
    val entryId: String get() = active.id
    val schemaLink: String? get() = active.schemaLink
    val actionsLink: String? get() = active.actionsLink
    val ruleFiles: List<String> get() = active.ruleFiles

    val isMultiEntry: Boolean get() = entries.size > 1

    fun entry(id: String): ProjectEntry? {
        return entries.firstOrNull { it.id == id }
    }

    fun withActive(transform: (ProjectEntry) -> ProjectEntry): ProjectSession {
        return withEntry(id = active.id, transform = transform)
    }

    /** Replaces one entry in place, keeping the order the manifest declared. */
    fun withEntry(id: String, transform: (ProjectEntry) -> ProjectEntry): ProjectSession {
        val target = entry(id = id) ?: return this
        val replacement = transform(target)
        return copy(
            entries = entries.map { entry -> if (entry.id == id) replacement else entry },
            // Following a rename keeps the buffers bound to the entry the user is editing.
            activeEntryId = if (activeEntryId == id) replacement.id else activeEntryId,
        )
    }

    fun missing(kind: ProjectFileKind): MissingProjectFile? {
        return missingFiles.firstOrNull { it.kind == kind }
    }

    companion object {
        /** A manifest with no `entries:` still has to be editable, so it opens with an empty one. */
        const val DEFAULT_ENTRY_ID: String = "default"

        fun singleEntry(
            root: Path,
            manifestFileName: String,
            entry: ProjectEntry,
            manifestName: String? = null,
        ): ProjectSession {
            return ProjectSession(
                root = root,
                manifestFileName = manifestFileName,
                entries = listOf(entry),
                activeEntryId = entry.id,
                manifestName = manifestName,
            )
        }
    }
}
