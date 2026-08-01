package ui.project

import ruleengine.manifest.ManifestPathResolution
import ruleengine.manifest.ManifestPathResolver
import java.nio.file.Files
import java.nio.file.Path

/**
 * Decides what may be erased when a manifest entry is dropped, and erases it.
 *
 * Removing an entry is the only place the workbench deletes anything, so the rules about what it may
 * touch are kept here, apart from the dialog that asks and pure enough to test without a filesystem:
 * a file another entry still references, or one that lives outside the project, is never the removed
 * entry's to delete.
 */
object ManifestEntryRemoval {

    /** The removed entry's files that nothing else claims — safe to erase. */
    fun deletableFiles(session: ProjectSession, entryId: String): List<ProjectEntryFile> {
        return classify(session = session, entryId = entryId).first
    }

    /** The removed entry's files that survive regardless: shared with another entry, or external. */
    fun keptFiles(session: ProjectSession, entryId: String): List<String> {
        return classify(session = session, entryId = entryId).second
    }

    /** Deletes [files], returning a message for each one that could not be removed. */
    fun delete(files: List<ProjectEntryFile>): List<String> {
        return files.mapNotNull { file ->
            runCatching { Files.deleteIfExists(file.path) }.exceptionOrNull()
                ?.let { ex -> "${file.relativePath}: ${ex.message ?: "could not be deleted"}" }
        }
    }

    private fun classify(session: ProjectSession, entryId: String): Pair<List<ProjectEntryFile>, List<String>> {
        val entry = session.entry(id = entryId) ?: return emptyList<ProjectEntryFile>() to emptyList()
        val claimedElsewhere = session.entries
            .filterNot { it.id == entryId }
            .flatMap { other -> listOfNotNull(other.schemaLink, other.actionsLink) + other.ruleFiles }
            .toSet()

        val deletable = mutableListOf<ProjectEntryFile>()
        val kept = mutableListOf<String>()

        referencedFiles(entry = entry).forEach { (kind, relativePath) ->
            val resolved = resolveInsideProject(session = session, relativePath = relativePath)
            when {
                relativePath in claimedElsewhere -> kept.add(relativePath)
                resolved == null -> kept.add(relativePath)
                else -> deletable.add(ProjectEntryFile(kind = kind, relativePath = relativePath, path = resolved))
            }
        }

        return deletable to kept
    }

    private fun referencedFiles(entry: ProjectEntry): List<Pair<ProjectFileKind, String>> {
        return buildList {
            entry.schemaLink?.let { link -> add(ProjectFileKind.SCHEMA to link) }
            entry.actionsLink?.let { link -> add(ProjectFileKind.ACTIONS to link) }
            entry.ruleFiles.forEach { rule -> add(ProjectFileKind.RULE to rule) }
        }
    }

    /**
     * The absolute path, but only for files the project owns.
     *
     * A `../shared/schema.yaml` is deliberately left alone: sharing is the reason such a link exists,
     * and other projects read it. The same resolver the loader uses draws the line, so "inside the
     * project" means the same thing on both sides.
     */
    private fun resolveInsideProject(session: ProjectSession, relativePath: String): Path? {
        if (ProjectPaths.isExternal(relativePath = relativePath)) return null
        return when (
            val resolution = ManifestPathResolver.resolveWithinBase(
                baseDir = session.root,
                relativePath = relativePath,
                label = "entry file",
            )
        ) {
            is ManifestPathResolution.Accepted -> resolution.path
            is ManifestPathResolution.Rejected -> null
        }
    }
}
