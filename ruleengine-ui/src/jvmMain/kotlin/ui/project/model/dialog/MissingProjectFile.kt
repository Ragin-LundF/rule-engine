package ui.project.model.dialog

import ui.project.model.ProjectFileKind

/**
 * A file the manifest references that could not be read.
 *
 * Collected rather than thrown: a project with one missing rule file still opens, so the user can
 * see what is broken and re-link or drop it, instead of being locked out of the whole project by a
 * path that went stale.
 */
data class MissingProjectFile(
    val kind: ProjectFileKind,
    val relativePath: String,
    val reason: String,
)
