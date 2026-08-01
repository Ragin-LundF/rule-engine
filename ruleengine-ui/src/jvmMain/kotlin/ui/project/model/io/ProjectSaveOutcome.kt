package ui.project.model.io

import ui.project.model.ProjectSession
import ui.project.model.dialog.ProjectSaveConfirmation

/**
 * Outcome of writing a project.
 *
 * [NeedsConfirmation] is what keeps the saver free of Compose: it stops before doing anything the
 * user has not agreed to, the UI asks, and the save is re-invoked with the answer. Without it the
 * saver would either have to own dialogs or quietly overwrite a schema other projects depend on.
 */
sealed interface ProjectSaveOutcome {

    data class Saved(val session: ProjectSession, val filesWritten: Int) : ProjectSaveOutcome

    data class NeedsConfirmation(val request: ProjectSaveConfirmation) : ProjectSaveOutcome

    data class Failed(val message: String) : ProjectSaveOutcome
}
