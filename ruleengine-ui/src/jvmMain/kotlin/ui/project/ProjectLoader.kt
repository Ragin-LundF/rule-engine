package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ManifestPathResolver
import ruleengine.manifest.ProjectManifest
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

        val entry = manifest.entries.firstOrNull() ?: ManifestEntry(id = "default")
        applyManifest(state = into, root = root, manifestText = manifestText, manifest = manifest, entry = entry)

        val missing = buildList {
            loadSchema(state = into, root = root, entry = entry)?.let(::add)
            loadActions(state = into, root = root, entry = entry)?.let(::add)
            addAll(loadRules(state = into, entry = entry))
        }

        return ProjectLoadResult.Loaded(
            session = ProjectSession(
                root = root,
                manifestFileName = normalizedManifest.fileName.toString(),
                entryId = entry.id,
                schemaLink = entry.schema,
                actionsLink = entry.actions,
                ruleFiles = entry.rules,
                isMultiEntry = manifest.entries.size > 1,
                missingFiles = missing,
            ),
        )
    }

    private fun applyManifest(
        state: RuleEditorState,
        root: Path,
        manifestText: String,
        manifest: ProjectManifest,
        entry: ManifestEntry,
    ) {
        state.manifestText.value = manifestText
        state.manifestFieldValue.value = TextFieldValue(text = manifestText)
        state.manifestBaseDir.value = root.toString()
        state.parsedManifest.value = manifest
        state.selectedManifestEntry.value = entry.id
    }

    private fun loadSchema(state: RuleEditorState, root: Path, entry: ManifestEntry): MissingProjectFile? {
        val relativePath = entry.schema ?: return null
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

    private fun loadActions(state: RuleEditorState, root: Path, entry: ManifestEntry): MissingProjectFile? {
        val relativePath = entry.actions ?: return null
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
    private fun loadRules(state: RuleEditorState, entry: ManifestEntry): List<MissingProjectFile> {
        if (entry.rules.isEmpty()) return emptyList()

        state.loadAllRuleFilesForCurrentEntry()
        val loadedPaths = state.entryRuleSources.value.map { it.relativePath }.toSet()

        state.showAllRules.value = false
        entry.rules.firstOrNull { it in loadedPaths }?.let { first ->
            state.loadSingleManifestRuleFile(relativePath = first)
            dirtyState.markClean(key = ProjectDirtyState.ruleKey(relativePath = first), content = state.ruleValue.value.text)
        }

        return entry.rules.filterNot { it in loadedPaths }.map { relativePath ->
            MissingProjectFile(
                kind = ProjectFileKind.RULE,
                relativePath = relativePath,
                reason = "not found",
            )
        }
    }
}
