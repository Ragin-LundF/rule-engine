package ui.project

import ruleengine.dsl.parser.Parser
import ruleengine.manifest.ManifestPathResolution
import ruleengine.manifest.ManifestPathResolver
import ui.editor.rules.RuleEditorState
import ui.manifest.EditableManifestEntry
import ui.manifest.ManifestEditorState
import ui.manifest.ManifestYamlBridge
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Writes the whole project to disk.
 *
 * "Save" used to mean one file, which is how a manifest could end up referring to rule files that
 * were never written. Here a save is the project: rule files, schema, actions and the manifest that
 * ties them together, with the manifest written last so a failure part-way through never leaves an
 * index pointing at content that does not exist.
 *
 * Every write is planned and approved before any of it happens, so the user answers all the
 * questions up front rather than half-way through a half-written project.
 */
class ProjectSaver(private val dirtyState: ProjectDirtyState) {

    private data class PlannedWrite(
        val kind: ProjectFileKind,
        val relativePath: String,
        val path: Path,
        val content: String,
        val dirtyKey: String,
    )

    fun save(
        state: RuleEditorState,
        session: ProjectSession,
        approvals: ProjectSaveApprovals = ProjectSaveApprovals(),
    ): ProjectSaveOutcome {
        if (session.isMultiEntry) {
            return ProjectSaveOutcome.Failed(
                message = "This manifest defines several entries. Use 'Save Project As…' to write a single-entry copy.",
            )
        }

        return runCatching { writeProject(state = state, session = session, approvals = approvals) }
            .getOrElse { ex -> ProjectSaveOutcome.Failed(message = ex.message ?: "Save failed") }
    }

    /**
     * Copies the project to a new root and saves it there.
     *
     * Rule files and any schema kept inside the project are copied, because they are part of the
     * project. A schema linked from outside stays where it is and only its relative path is
     * rewritten for the new depth — copying it would fork a file whose whole purpose is being shared.
     */
    fun saveAs(
        state: RuleEditorState,
        session: ProjectSession,
        newManifestPath: Path,
        approvals: ProjectSaveApprovals = ProjectSaveApprovals(),
    ): ProjectSaveOutcome {
        val newRoot = newManifestPath.toAbsolutePath().normalize().parent
            ?: return ProjectSaveOutcome.Failed(message = "Chosen location has no parent directory")

        return runCatching {
            Files.createDirectories(ProjectPaths.rulesDir(root = newRoot))
            Files.createDirectories(ProjectPaths.schemasDir(root = newRoot))

            session.ruleFiles.forEach { relativePath -> copyIntoNewRoot(session, newRoot, relativePath) }

            val relocated = session.copy(
                root = newRoot,
                manifestFileName = newManifestPath.fileName.toString(),
                schemaLink = relocateLink(session = session, newRoot = newRoot, link = session.schemaLink),
                actionsLink = relocateLink(session = session, newRoot = newRoot, link = session.actionsLink),
                isMultiEntry = false,
                missingFiles = emptyList(),
            )

            // The copies are new files; their baselines belong to the old root.
            dirtyState.clear()
            writeProject(state = state, session = relocated, approvals = approvals)
        }.getOrElse { ex -> ProjectSaveOutcome.Failed(message = ex.message ?: "Save As failed") }
    }

    private fun copyIntoNewRoot(session: ProjectSession, newRoot: Path, relativePath: String) {
        val source = resolveRulePath(root = session.root, relativePath = relativePath) ?: return
        val target = resolveRulePath(root = newRoot, relativePath = relativePath) ?: return
        if (!Files.exists(source) || source == target) return
        target.parent?.let { parent -> Files.createDirectories(parent) }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * A link inside the project keeps its relative path; one outside is re-expressed against the new
     * root so it still points at the same file.
     */
    private fun relocateLink(session: ProjectSession, newRoot: Path, link: String?): String? {
        if (link == null || !ProjectPaths.isExternal(relativePath = link)) return link
        val absolute = ManifestPathResolver.resolveAllowingEscape(baseDir = session.root, relativePath = link)
        return ProjectPaths.relativize(root = newRoot, target = absolute)
    }

    private fun writeProject(
        state: RuleEditorState,
        session: ProjectSession,
        approvals: ProjectSaveApprovals,
    ): ProjectSaveOutcome {
        Files.createDirectories(ProjectPaths.rulesDir(root = session.root))
        Files.createDirectories(ProjectPaths.schemasDir(root = session.root))

        val ruleWrite = planRuleWrite(state = state, session = session)
        val schemaWrite = planSchemaWrite(state = state, session = session)
        val actionsWrite = planActionsWrite(state = state, session = session)

        val updatedSession = session.copy(
            schemaLink = schemaWrite?.relativePath ?: session.schemaLink,
            actionsLink = actionsWrite?.relativePath ?: session.actionsLink,
            ruleFiles = mergeRuleFiles(session = session, added = ruleWrite?.relativePath),
        )

        val contentWrites = listOfNotNull(ruleWrite, schemaWrite, actionsWrite)
            .filter { write -> currentContent(path = write.path) != write.content }

        contentWrites.forEach { write ->
            blockingConfirmation(write = write, approvals = approvals)?.let { confirmation ->
                return ProjectSaveOutcome.NeedsConfirmation(request = confirmation)
            }
        }

        contentWrites.forEach { write -> writeFile(write = write) }

        // The buffer now has a file behind it, so later saves update that file rather than creating
        // another one, and switching rule files knows what it is leaving.
        ruleWrite?.let { write -> state.selectedManifestRuleFile.value = write.relativePath }

        // Last, so an aborted save never leaves a manifest indexing files that were not written.
        val manifestWrite = planManifestWrite(state = state, session = updatedSession)
        writeFile(write = manifestWrite)

        return ProjectSaveOutcome.Saved(
            session = updatedSession,
            filesWritten = contentWrites.size + 1,
        )
    }

    /**
     * The open rule buffer, written back to the file it came from.
     *
     * Skipped in all-files mode: that buffer is several files joined for reading, and writing it
     * back would collapse the whole entry into whichever file happens to be selected.
     */
    private fun planRuleWrite(state: RuleEditorState, session: ProjectSession): PlannedWrite? {
        if (state.showAllRules.value) return null
        val content = state.ruleValue.value.text
        if (content.isBlank() && state.selectedManifestRuleFile.value == null) return null

        // The session's file wins over a new name when the buffer has no on-disk identity yet:
        // after a first save the editor is bound to the file that was written, so a later save
        // updates it instead of inventing a second file from whatever the buffer now parses to.
        val relativePath = state.selectedManifestRuleFile.value
            ?: session.ruleFiles.singleOrNull()
            ?: "${ProjectPaths.RULES_DIR}/${defaultRuleFileName(ruleText = content)}"

        val path = resolveRulePath(root = session.root, relativePath = relativePath)
            ?: return null

        return PlannedWrite(
            kind = ProjectFileKind.RULE,
            relativePath = relativePath,
            path = path,
            content = content,
            dirtyKey = ProjectDirtyState.ruleKey(relativePath = relativePath),
        )
    }

    private fun planSchemaWrite(state: RuleEditorState, session: ProjectSession): PlannedWrite? {
        val content = state.schemaText.value
        if (content.isBlank() && session.schemaLink == null) return null

        val relativePath = session.schemaLink
            ?: "${ProjectPaths.SCHEMAS_DIR}/${ProjectPaths.DEFAULT_SCHEMA_FILE}"

        return PlannedWrite(
            kind = ProjectFileKind.SCHEMA,
            relativePath = relativePath,
            path = ManifestPathResolver.resolveAllowingEscape(baseDir = session.root, relativePath = relativePath),
            content = content,
            dirtyKey = ProjectDirtyState.SCHEMA,
        )
    }

    private fun planActionsWrite(state: RuleEditorState, session: ProjectSession): PlannedWrite? {
        val content = state.actionSchemaText.value
        if (content.isBlank() && session.actionsLink == null) return null

        val relativePath = session.actionsLink
            ?: "${ProjectPaths.SCHEMAS_DIR}/${ProjectPaths.DEFAULT_ACTIONS_FILE}"

        return PlannedWrite(
            kind = ProjectFileKind.ACTIONS,
            relativePath = relativePath,
            path = ManifestPathResolver.resolveAllowingEscape(baseDir = session.root, relativePath = relativePath),
            content = content,
            dirtyKey = ProjectDirtyState.ACTIONS,
        )
    }

    private fun planManifestWrite(state: RuleEditorState, session: ProjectSession): PlannedWrite {
        val yaml = ManifestYamlBridge.toYaml(
            state = ManifestEditorState(
                name = state.parsedManifest.value?.name ?: session.displayName,
                entries = listOf(
                    EditableManifestEntry(
                        id = session.entryId,
                        schemaPath = session.schemaLink.orEmpty(),
                        actionsPath = session.actionsLink.orEmpty(),
                        rulePaths = session.ruleFiles,
                    ),
                ),
            ),
        )

        return PlannedWrite(
            kind = ProjectFileKind.RULE,
            relativePath = session.manifestFileName,
            path = session.manifestPath,
            content = yaml,
            dirtyKey = session.manifestFileName,
        )
    }

    /**
     * Whether this write needs the user's word first.
     *
     * Two cases: it leaves the project, or the file moved on underneath us. Both would otherwise
     * destroy something the user never saw.
     */
    private fun blockingConfirmation(
        write: PlannedWrite,
        approvals: ProjectSaveApprovals,
    ): ProjectSaveConfirmation? {
        if (ProjectPaths.isExternal(relativePath = write.relativePath) &&
            !approvals.allowsExternalWrite(kind = write.kind)
        ) {
            return ProjectSaveConfirmation.ExternalWrite(kind = write.kind, relativePath = write.relativePath)
        }

        val onDisk = currentContent(path = write.path) ?: return null
        val baseline = dirtyState.baseline(key = write.dirtyKey) ?: return null
        if (onDisk != baseline && !approvals.allowsOverwrite(relativePath = write.relativePath)) {
            return ProjectSaveConfirmation.DiskConflict(kind = write.kind, relativePath = write.relativePath)
        }
        return null
    }

    private fun writeFile(write: PlannedWrite) {
        write.path.parent?.let { parent -> Files.createDirectories(parent) }
        Files.writeString(write.path, write.content)
        dirtyState.markClean(key = write.dirtyKey, content = write.content)
    }

    private fun currentContent(path: Path): String? = runCatching { Files.readString(path) }.getOrNull()

    private fun resolveRulePath(root: Path, relativePath: String): Path? {
        return when (
            val resolution = ManifestPathResolver.resolveWithinBase(
                baseDir = root,
                relativePath = relativePath,
                label = "rule",
            )
        ) {
            is ManifestPathResolution.Accepted -> resolution.path
            is ManifestPathResolution.Rejected -> null
        }
    }

    private fun mergeRuleFiles(session: ProjectSession, added: String?): List<String> {
        if (added == null || added in session.ruleFiles) return session.ruleFiles
        return session.ruleFiles + added
    }

    /** Names a brand-new rule file after the first rule in it, which is what the user just wrote. */
    private fun defaultRuleFileName(ruleText: String): String {
        val ruleId = runCatching { Parser(input = ruleText).parseRules().firstOrNull()?.id }.getOrNull()
        val slug = ruleId?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.replace(regex = Regex(pattern = "[^a-z0-9]+"), replacement = "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
        return "${slug ?: "rules"}.rule"
    }
}
