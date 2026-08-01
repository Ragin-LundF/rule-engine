package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ManifestPathResolver
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.editor.rules.RuleEditorState
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opens a project from disk into the editor state.
 *
 * The order here is the whole contract: parse first, clear second, populate third. Clearing before
 * the manifest is known to be readable would destroy a working project to open a broken one, and
 * *not* clearing at all is what used to make the second load a no-op that silently mixed two
 * projects together.
 *
 * A manifest may hold several independent entries. All of them become part of the session, but only
 * the active one is read from disk — the buffers show one entry at a time, and reading the others
 * would report missing files for content the user is not looking at.
 */
class ProjectLoader(private val dirtyState: ProjectDirtyState) {

    fun load(manifestPath: Path, into: RuleEditorState): ProjectLoadResult {
        val normalizedManifest = manifestPath.toAbsolutePath().normalize()
        val root = normalizedManifest.parent
            ?: return ProjectLoadResult.Failed(message = "Manifest has no parent directory")

        val manifestText = runCatching { Files.readString(normalizedManifest) }
            .getOrElse { ex ->
                return ProjectLoadResult.Failed(message = "Cannot read ${normalizedManifest.fileName}: ${ex.message}")
            }

        val manifest = runCatching { ManifestLoader.loadFromString(content = manifestText) }
            .getOrElse { ex ->
                return ProjectLoadResult.Failed(message = "Invalid manifest: ${ex.message}")
            }

        // Past this point the project is known to be openable, so replacing the current one is safe.
        into.reset()
        dirtyState.clear()

        val entries = manifest.entries
            .map { entry ->
                ProjectEntry(
                    id = entry.id,
                    schemaLink = entry.schema,
                    actionsLink = entry.actions,
                    ruleFiles = entry.rules,
                )
            }
            .ifEmpty { listOf(ProjectEntry(id = ProjectSession.DEFAULT_ENTRY_ID)) }

        val session = ProjectSession(
            root = root,
            manifestFileName = normalizedManifest.fileName.toString(),
            entries = entries,
            activeEntryId = entries.first().id,
            manifestName = manifest.name,
        )

        into.manifestText.value = manifestText
        into.manifestFieldValue.value = TextFieldValue(text = manifestText)
        into.manifestBaseDir.value = root.toString()
        into.parsedManifest.value = manifest

        val missing = activate(session = session, entryId = entries.first().id, into = into)
        return ProjectLoadResult.Loaded(session = session.copy(missingFiles = missing))
    }

    /**
     * Points the editor buffers at [entryId] and reads that entry's files.
     *
     * Used both by [load] and by switching entries, so a switch goes through exactly the same
     * reading and baselining as an open — anything else and a switched-to entry would look dirty the
     * moment it appeared.
     */
    fun activate(session: ProjectSession, entryId: String, into: RuleEditorState): List<MissingProjectFile> {
        val entry = session.entry(id = entryId) ?: return emptyList()

        into.resetEntryBuffers()
        dirtyState.forget(key = ProjectDirtyState.SCHEMA)
        dirtyState.forget(key = ProjectDirtyState.ACTIONS)
        into.selectedManifestEntry.value = entry.id

        return buildList {
            loadSchema(state = into, root = session.root, entry = entry)?.let(::add)
            loadActions(state = into, root = session.root, entry = entry)?.let(::add)
            addAll(loadRules(state = into, entry = entry))
        }
    }

    private fun loadSchema(state: RuleEditorState, root: Path, entry: ProjectEntry): MissingProjectFile? {
        val relativePath = entry.schemaLink ?: return null
        return runCatching {
            val path = ManifestPathResolver.resolveAllowingEscape(baseDir = root, relativePath = relativePath)
            val content = Files.readString(path)
            state.schemaText.value = content
            state.schemaFieldValue.value = TextFieldValue(text = content)
            state.parsedSchema.value = runCatching {
                FieldSchemaLoader.loadFromString(content = content, nameHint = path.fileName.toString())
            }.getOrNull()
            dirtyState.markClean(key = ProjectDirtyState.SCHEMA, content = content)
            null
        }.getOrElse { ex ->
            MissingProjectFile(
                kind = ProjectFileKind.SCHEMA,
                relativePath = relativePath,
                reason = ex.message ?: "not readable",
            )
        }
    }

    private fun loadActions(state: RuleEditorState, root: Path, entry: ProjectEntry): MissingProjectFile? {
        val relativePath = entry.actionsLink ?: return null
        return runCatching {
            val path = ManifestPathResolver.resolveAllowingEscape(baseDir = root, relativePath = relativePath)
            val content = Files.readString(path)
            state.actionSchemaText.value = content
            state.actionFieldValue.value = TextFieldValue(text = content)
            state.parsedActionSchema.value = runCatching {
                ActionSchemaLoader.loadFromString(content = content)
            }.getOrNull()
            dirtyState.markClean(key = ProjectDirtyState.ACTIONS, content = content)
            null
        }.getOrElse { ex ->
            MissingProjectFile(
                kind = ProjectFileKind.ACTIONS,
                relativePath = relativePath,
                reason = ex.message ?: "not readable",
            )
        }
    }

    /**
     * Reads every rule file the entry lists, then opens the first one that survived.
     *
     * All of them are read rather than just the first because the rule tree, the diagrams and the
     * overview export all need the full set; the editor still shows one file at a time.
     */
    private fun loadRules(state: RuleEditorState, entry: ProjectEntry): List<MissingProjectFile> {
        if (entry.ruleFiles.isEmpty()) return emptyList()

        state.loadRuleFiles(relativePaths = entry.ruleFiles)
        val loadedPaths = state.entryRuleSources.value.map { it.relativePath }.toSet()

        state.showAllRules.value = false
        entry.ruleFiles.firstOrNull { it in loadedPaths }?.let { first ->
            state.loadSingleManifestRuleFile(relativePath = first)
            dirtyState.markClean(
                key = ProjectDirtyState.ruleKey(relativePath = first),
                content = state.ruleValue.value.text,
            )
        }

        return entry.ruleFiles.filterNot { it in loadedPaths }.map { relativePath ->
            MissingProjectFile(
                kind = ProjectFileKind.RULE,
                relativePath = relativePath,
                reason = "not found",
            )
        }
    }
}
