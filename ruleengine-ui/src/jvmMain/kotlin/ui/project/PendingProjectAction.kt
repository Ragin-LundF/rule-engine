package ui.project

/** What to resume once the unsaved-changes question has been answered. */
sealed interface PendingProjectAction {

    data object OpenProject : PendingProjectAction

    data object NewProject : PendingProjectAction

    data object CloseWindow : PendingProjectAction

    /** Carries the target, so answering the question does not lose which entry was being opened. */
    data class SwitchEntry(val entryId: String) : PendingProjectAction
}
