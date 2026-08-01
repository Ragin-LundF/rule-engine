package ui.project.model.io

import ui.project.model.ProjectSession

/**
 * Outcome of opening a project.
 *
 * [Failed] means nothing was touched: the manifest is parsed before any editor state is cleared, so
 * a broken file leaves the project already open exactly as it was. Reporting that honestly is the
 * point — the previous behaviour swallowed the parse error and said "Manifest loaded".
 */
sealed interface ProjectLoadResult {

    data class Loaded(val session: ProjectSession) : ProjectLoadResult

    data class Failed(val message: String) : ProjectLoadResult
}
