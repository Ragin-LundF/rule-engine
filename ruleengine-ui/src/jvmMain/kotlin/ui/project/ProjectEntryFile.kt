package ui.project

import java.nio.file.Path

/** A file a manifest entry references, resolved against the project root. */
data class ProjectEntryFile(
    val kind: ProjectFileKind,
    val relativePath: String,
    val path: Path,
)
