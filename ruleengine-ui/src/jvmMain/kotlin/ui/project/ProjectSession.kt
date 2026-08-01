package ui.project

import java.nio.file.Path

/**
 * The on-disk identity of the project currently open.
 *
 * This is what the editor state has never had: buffers alone cannot say where they came from, which
 * is why a loaded schema could never be saved back and why loading a second manifest left the first
 * one's content on screen. A null session means "scratch" — everything is in memory and the first
 * save asks where to put it.
 *
 * Immutable; the UI holds it in a `mutableStateOf` and replaces it on every load and save.
 */
data class ProjectSession(
    val root: Path,
    val manifestFileName: String,
    val entryId: String,
    val schemaLink: String? = null,
    val actionsLink: String? = null,
    val ruleFiles: List<String> = emptyList(),
    val isMultiEntry: Boolean = false,
    val missingFiles: List<MissingProjectFile> = emptyList(),
) {
    val manifestPath: Path get() = root.resolve(manifestFileName)

    /** Shown in the title and status bars — the folder name, which is how users name projects. */
    val displayName: String get() = root.fileName?.toString() ?: root.toString()

    fun missing(kind: ProjectFileKind): MissingProjectFile? = missingFiles.firstOrNull { it.kind == kind }
}
