package ui.project

/**
 * Confirmations the user has already given for this save attempt.
 *
 * Carried in rather than remembered by the saver so that each save starts from "nothing approved":
 * agreeing once to overwrite a shared schema should not silently apply to every later save.
 */
data class ProjectSaveApprovals(
    val externalWrites: Set<ProjectFileKind> = emptySet(),
    val overwrittenConflicts: Set<String> = emptySet(),
) {
    fun allowsExternalWrite(kind: ProjectFileKind): Boolean = kind in externalWrites

    fun allowsOverwrite(relativePath: String): Boolean = relativePath in overwrittenConflicts
}
