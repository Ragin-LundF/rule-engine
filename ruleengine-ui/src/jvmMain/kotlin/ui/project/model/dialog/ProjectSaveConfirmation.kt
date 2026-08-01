package ui.project.model.dialog

import ui.project.model.ProjectFileKind

/** Something the saver will not do without being told to. */
sealed interface ProjectSaveConfirmation {

    /**
     * The linked file lives outside the project and has local edits. Writing it changes a file other
     * projects may read, so it is never done silently.
     */
    data class ExternalWrite(
        val kind: ProjectFileKind,
        val relativePath: String,
    ) : ProjectSaveConfirmation

    /** The file changed on disk since it was read — saving would discard someone else's edit. */
    data class DiskConflict(
        val kind: ProjectFileKind,
        val relativePath: String,
    ) : ProjectSaveConfirmation
}
