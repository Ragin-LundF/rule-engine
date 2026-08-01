package ui.project.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import ui.components.ConfirmDialog
import ui.project.ProjectWorkspace
import ui.project.model.dialog.ProjectDialog

/**
 * Renders whichever question [ProjectWorkspace] is currently asking.
 *
 * One host rather than dialogs scattered through the toolbar and the area screens, so that the
 * workspace can raise a question from anywhere — including from the middle of a save it had to stop.
 */
@Composable
fun ProjectDialogHost(workspace: ProjectWorkspace) {
    val dialog by workspace.dialog

    when (val current = dialog) {
        null -> Unit

        is ProjectDialog.UnsavedChanges -> ConfirmDialog(
            title = "Unsaved changes",
            message = "${current.projectName} has unsaved changes.",
            confirmLabel = "Save",
            onConfirm = { workspace.onUnsavedChangesSave(pending = current.pending) },
            neutralLabel = "Discard",
            onNeutral = { workspace.onUnsavedChangesDiscard(pending = current.pending) },
            onDismiss = workspace::dismissDialog,
        )

        is ProjectDialog.ExternalWrite -> ConfirmDialog(
            title = "Shared ${current.request.kind.label} file",
            message = "The ${current.request.kind.label} is shared at ${current.request.relativePath}. " +
                    "Saving there changes a file other projects may use.",
            confirmLabel = "Save there",
            onConfirm = { workspace.onApproveExternalWrite(kind = current.request.kind) },
            neutralLabel = "Copy into project",
            onNeutral = { workspace.onCopyExternalIntoProject(kind = current.request.kind) },
            onDismiss = workspace::dismissDialog,
        )

        is ProjectDialog.DiskConflict -> ConfirmDialog(
            title = "Changed on disk",
            message = "${current.request.relativePath} changed on disk since it was opened. " +
                    "Saving now would discard those changes.",
            confirmLabel = "Overwrite",
            onConfirm = { workspace.onOverwriteConflict(relativePath = current.request.relativePath) },
            neutralLabel = "Reload from disk",
            onNeutral = workspace::onReloadConflict,
            onDismiss = workspace::dismissDialog,
        )

        is ProjectDialog.LinkAfterExport -> ConfirmDialog(
            title = "Link to the exported file?",
            message = "Saved to ${current.exported}. Link this project to it so both stay in sync?",
            confirmLabel = "Link project",
            onConfirm = { workspace.onLinkAfterExport(kind = current.kind, exported = current.exported) },
            dismissLabel = "Keep current link",
            onDismiss = workspace::dismissDialog,
        )

        is ProjectDialog.RemoveEntry -> RemoveEntryDialog(request = current, workspace = workspace)

        is ProjectDialog.Error -> ConfirmDialog(
            title = current.title,
            message = current.message,
            confirmLabel = "OK",
            onConfirm = workspace::dismissDialog,
            dismissLabel = "Close",
            onDismiss = workspace::dismissDialog,
        )
    }
}

/**
 * Removing an entry: erase what it owned, or only stop the manifest naming it.
 *
 * "Delete files" is the destructive answer and is only offered when the entry exclusively owns
 * something; with nothing to erase, the question collapses to a plain confirmation.
 */
@Composable
private fun RemoveEntryDialog(request: ProjectDialog.RemoveEntry, workspace: ProjectWorkspace) {
    val hasOwnFiles = request.deletable.isNotEmpty()
    ConfirmDialog(
        title = "Remove entry ${request.entryId}",
        message = removeEntryMessage(dialog = request),
        confirmLabel = if (hasOwnFiles) "Delete files" else "Remove from manifest",
        onConfirm = {
            if (hasOwnFiles) {
                workspace.onRemoveEntryDeletingFiles(entryId = request.entryId)
            } else {
                workspace.onRemoveEntryKeepingFiles(entryId = request.entryId)
            }
        },
        neutralLabel = "Remove from manifest".takeIf { hasOwnFiles },
        onNeutral = { workspace.onRemoveEntryKeepingFiles(entryId = request.entryId) },
        onDismiss = workspace::dismissDialog,
    )
}

/** Names the files on both sides of the question, since "delete" does not mean all of them. */
private fun removeEntryMessage(dialog: ProjectDialog.RemoveEntry): String {
    return buildString {
        append("Remove ${dialog.entryId} from the manifest.")
        if (dialog.deletable.isNotEmpty()) {
            append("\n\nThese files belong to it alone and can be deleted:\n")
            append(dialog.deletable.joinToString(separator = "\n") { "• ${it.relativePath}" })
        }
        if (dialog.shared.isNotEmpty()) {
            append("\n\nKept either way — shared with another entry or outside the project:\n")
            append(dialog.shared.joinToString(separator = "\n") { "• $it" })
        }
    }
}
