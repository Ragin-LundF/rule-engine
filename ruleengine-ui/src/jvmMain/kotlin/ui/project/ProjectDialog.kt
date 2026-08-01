package ui.project

import java.nio.file.Path

/** A question the project workflow is currently asking the user. Null means no dialog is open. */
sealed interface ProjectDialog {

    /**
     * Something would discard unsaved work. [pending] is what to do once the user has decided,
     * so that "Save" can finish the interrupted action instead of leaving the user to repeat it.
     */
    data class UnsavedChanges(val projectName: String, val pending: PendingProjectAction) : ProjectDialog

    data class ExternalWrite(val request: ProjectSaveConfirmation.ExternalWrite) : ProjectDialog

    data class DiskConflict(val request: ProjectSaveConfirmation.DiskConflict) : ProjectDialog

    /** Offered after exporting a schema, since exporting is how a shared schema comes to exist. */
    data class LinkAfterExport(val kind: ProjectFileKind, val exported: Path) : ProjectDialog

    /**
     * Dropping an entry: are its files rubbish now, or do they outlive the entry?
     *
     * [deletable] is what the entry owns exclusively; [shared] is what stays regardless because
     * another entry uses it or it lives outside the project, which the user needs told rather than
     * discovering afterwards that "delete" did not.
     */
    data class RemoveEntry(
        val entryId: String,
        val deletable: List<ProjectEntryFile>,
        val shared: List<String>,
    ) : ProjectDialog

    data class Error(val title: String, val message: String) : ProjectDialog
}
