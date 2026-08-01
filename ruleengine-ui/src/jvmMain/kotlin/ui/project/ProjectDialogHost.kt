package ui.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import ui.components.ConfirmDialog

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
