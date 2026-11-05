package ui.project.io

import ruleengine.dsl.parser.Parser
import ruleengine.manifest.ManifestPathResolver
import ui.editor.rules.RuleEditorState
import ui.manifest.ManifestYamlBridge
import ui.project.ProjectDirtyState
import ui.project.ProjectPaths
import ui.project.manifest.toEditorState
import ui.project.model.ProjectFileKind
import ui.project.model.ProjectSession
import ui.project.model.dialog.ProjectSaveApprovals
import ui.project.model.dialog.ProjectSaveConfirmation
import ui.project.model.io.ProjectSaveOutcome
import java.nio.file.Files
import java.nio.file.Path

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

    private companion object {
        /** What [Files.mismatch] answers for two files with the same bytes. */
        const val NO_MISMATCH = -1L
    }

    private data class PlannedWrite(
        val kind: ProjectFileKind,
        val relativePath: String,
        val path: Path,
        val content: String,
        val dirtyKey: String,
    )

    /**
     * Writes the open buffers, every rule file of the active entry edited in the working copy, and the
     * manifest.
     *
     * Only the active entry has buffers, so only its files are written; the other entries are carried
     * through untouched and re-emitted into the manifest, which is the whole point of holding them in
     * the session rather than dropping everything but the one on screen.
     */
    fun save(
        state: RuleEditorState,
        session: ProjectSession,
        approvals: ProjectSaveApprovals = ProjectSaveApprovals(),
    ): ProjectSaveOutcome {
        return runCatching { writeProject(state = state, session = session, approvals = approvals) }
            .getOrElse { ex -> ProjectSaveOutcome.Failed(message = ex.message ?: "Save failed") }
    }

    /**
     * Writes the manifest alone, leaving every content file as it is.
     *
     * Removing an entry deletes files immediately, so the index has to stop naming them at the same
     * moment; waiting for the next full save would leave a manifest on disk pointing at nothing.
     */
    fun saveManifest(session: ProjectSession): ProjectSaveOutcome {
        return runCatching {
            writeFile(write = planManifestWrite(session = session))
            ProjectSaveOutcome.Saved(session = session, filesWritten = 1)
        }.getOrElse { ex -> ProjectSaveOutcome.Failed(message = ex.message ?: "Save failed") }
    }

    /**
     * Copies the project to a new root and saves it there.
     *
     * Rule files and any schema kept inside the project are copied, because they are part of the
     * project. A schema, actions or rule file linked from outside stays where it is and only its
     * relative path is rewritten for the new depth — copying it would fork a file whose whole purpose
     * is being shared. A different file already at a copy's target is replaced only after confirmation.
     *
     * Nothing is copied or written until every question is answered. A save that does not go through
     * leaves the editor exactly as it was: buffers keyed by the old links, baselines of the old root.
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
            val copies = planCopies(session = session, newRoot = newRoot)
            // Asked before anything is copied: the folder may hold another project's file at the same path.
            copyConflict(copies = copies, approvals = approvals)?.let { conflict ->
                return ProjectSaveOutcome.NeedsConfirmation(request = conflict)
            }

            val ruleFileMoves = session.entries
                .flatMap { entry -> entry.ruleFiles }
                .associateWith { link -> relocateLink(session = session, newRoot = newRoot, link = link) ?: link }
                .filter { (old, new) -> old != new }
            val relocated = relocateSession(
                session = session,
                newManifestPath = newManifestPath,
                newRoot = newRoot,
                ruleFileMoves = ruleFileMoves,
            )
            saveIntoNewRoot(
                state = state,
                session = session,
                relocated = relocated,
                copies = copies,
                ruleFileMoves = ruleFileMoves,
                approvals = approvals,
            )
        }.getOrElse { ex -> ProjectSaveOutcome.Failed(message = ex.message ?: "Save As failed") }
    }

    private fun saveIntoNewRoot(
        state: RuleEditorState,
        session: ProjectSession,
        relocated: ProjectSession,
        copies: List<PlannedCopy>,
        ruleFileMoves: Map<String, String>,
        approvals: ProjectSaveApprovals,
    ): ProjectSaveOutcome {
        val oldBaselines = dirtyState.snapshot()
        // Baselines belong to the old root; a copy's baseline is what it will be once copied.
        dirtyState.clear()
        copies.forEach { copy -> copy.dirtyKey?.let { key -> dirtyState.markClean(key = key, content = copy.content) } }
        carryExternalBaselines(oldBaselines = oldBaselines, session = session, ruleFileMoves = ruleFileMoves)
        renameRuleBuffers(state = state, moves = ruleFileMoves)

        val outcome = runCatching {
            writeProject(state = state, session = relocated, approvals = approvals, pendingCopies = copies)
        }.getOrElse { ex -> ProjectSaveOutcome.Failed(message = ex.message ?: "Save As failed") }

        // Not saved: the session stays at the old root, so the buffers and baselines must stay with it.
        if (outcome !is ProjectSaveOutcome.Saved) {
            dirtyState.restore(snapshot = oldBaselines)
            renameRuleBuffers(state = state, moves = ruleFileMoves.entries.associate { (old, new) -> new to old })
        }
        return outcome
    }

    /**
     * A shared file outside the project is re-pointed, not copied: it is the same file, so it keeps the
     * baseline it was read with. Without it a teammate's change on disk would be overwritten unasked.
     */
    private fun carryExternalBaselines(
        oldBaselines: Map<String, String>,
        session: ProjectSession,
        ruleFileMoves: Map<String, String>,
    ) {
        fun carry(oldKey: String, newKey: String) {
            oldBaselines[oldKey]?.let { baseline -> dirtyState.markClean(key = newKey, content = baseline) }
        }
        ruleFileMoves.forEach { (old, new) ->
            carry(
                oldKey = ProjectDirtyState.ruleKey(relativePath = old),
                newKey = ProjectDirtyState.ruleKey(relativePath = new),
            )
        }
        if (session.schemaLink?.let { link -> ProjectPaths.isExternal(relativePath = link) } == true) {
            carry(oldKey = ProjectDirtyState.SCHEMA, newKey = ProjectDirtyState.SCHEMA)
        }
        if (session.actionsLink?.let { link -> ProjectPaths.isExternal(relativePath = link) } == true) {
            carry(oldKey = ProjectDirtyState.ACTIONS, newKey = ProjectDirtyState.ACTIONS)
        }
    }

    /**
     * Every file the project owns, of every entry: the copy is the whole project or it is broken.
     *
     * The active entry's schema and actions are left out — [writeProject] writes them from their buffers.
     */
    private fun planCopies(session: ProjectSession, newRoot: Path): List<PlannedCopy> {
        val ruleCopies = session.entries
            .flatMap { entry -> entry.ruleFiles }
            .distinct()
            .mapNotNull { relativePath ->
                planCopy(
                    session = session,
                    newRoot = newRoot,
                    relativePath = relativePath,
                    kind = ProjectFileKind.RULE,
                    dirtyKey = ProjectDirtyState.ruleKey(relativePath = relativePath),
                )
            }
        val writtenFromBuffers = setOfNotNull(session.schemaLink, session.actionsLink)
        val schemaCopies = session.entries
            .flatMap { entry ->
                listOfNotNull(
                    entry.schemaLink?.let { link -> link to ProjectFileKind.SCHEMA },
                    entry.actionsLink?.let { link -> link to ProjectFileKind.ACTIONS },
                )
            }
            .filter { (link, _) -> link !in writtenFromBuffers }
            .distinctBy { (link, _) -> link }
            .mapNotNull { (link, kind) ->
                planCopy(session = session, newRoot = newRoot, relativePath = link, kind = kind, dirtyKey = null)
            }
        return ruleCopies + schemaCopies
    }

    private fun copyConflict(copies: List<PlannedCopy>, approvals: ProjectSaveApprovals): ProjectSaveConfirmation? {
        val conflict = copies.firstOrNull { copy ->
            copy.replacesUnreadFile && !approvals.allowsOverwrite(relativePath = copy.relativePath)
        } ?: return null
        return ProjectSaveConfirmation.DiskConflict(kind = conflict.kind, relativePath = conflict.relativePath)
    }

    private fun relocateSession(
        session: ProjectSession,
        newManifestPath: Path,
        newRoot: Path,
        ruleFileMoves: Map<String, String>,
    ): ProjectSession {
        return session.copy(
            root = newRoot,
            manifestFileName = newManifestPath.fileName.toString(),
            entries = session.entries.map { entry ->
                entry.copy(
                    schemaLink = relocateLink(session = session, newRoot = newRoot, link = entry.schemaLink),
                    actionsLink = relocateLink(session = session, newRoot = newRoot, link = entry.actionsLink),
                    ruleFiles = entry.ruleFiles.map { link -> ruleFileMoves[link] ?: link },
                )
            },
            missingFiles = emptyList(),
        )
    }

    /** [dirtyKey] is null for a file no buffer tracks: the schema or actions of an entry not on screen. */
    private data class PlannedCopy(
        val kind: ProjectFileKind,
        val relativePath: String,
        val source: Path,
        val target: Path,
        val content: String,
        val dirtyKey: String?,
        val replacesUnreadFile: Boolean,
    )

    private fun planCopy(
        session: ProjectSession,
        newRoot: Path,
        relativePath: String,
        kind: ProjectFileKind,
        dirtyKey: String?,
    ): PlannedCopy? {
        // A file outside the project (`../shared/x.rule`) is shared, not owned: its link is re-pointed
        // instead, and it is never copied over whatever sits at the same relative spot next to the new root.
        if (ProjectPaths.isExternal(relativePath = relativePath)) return null
        val source = resolveRulePath(root = session.root, relativePath = relativePath)
        val target = resolveRulePath(root = newRoot, relativePath = relativePath)
        if (!Files.exists(source) || source == target) return null
        return PlannedCopy(
            kind = kind,
            relativePath = relativePath,
            source = source,
            target = target,
            content = Files.readString(source),
            dirtyKey = dirtyKey,
            replacesUnreadFile = Files.exists(target) && Files.mismatch(source, target) != NO_MISMATCH,
        )
    }

    private fun copyIntoNewRoot(copy: PlannedCopy) {
        copy.target.parent?.let { parent -> Files.createDirectories(parent) }
        // Replacing is safe here: a different file at the target was confirmed before anything was copied.
        // Written like every other project file, so a symlink at the target is written through.
        AtomicFiles.write(path = copy.target, bytes = Files.readAllBytes(copy.source))
    }

    /** The open file and the working copy are keyed by link, so they follow a re-pointed rule file. */
    private fun renameRuleBuffers(state: RuleEditorState, moves: Map<String, String>) {
        if (moves.isEmpty()) return
        state.selectedManifestRuleFile.value = state.selectedManifestRuleFile.value?.let { link -> moves[link] ?: link }
        state.inMemoryRuleFiles.value = state.inMemoryRuleFiles.value.mapKeys { (link, _) -> moves[link] ?: link }
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

    /**
     * [pendingCopies] are Save As copies not made yet: every check treats them as already on disk, and
     * they are made only once nothing is left to ask.
     */
    private fun writeProject(
        state: RuleEditorState,
        session: ProjectSession,
        approvals: ProjectSaveApprovals,
        pendingCopies: List<PlannedCopy> = emptyList(),
    ): ProjectSaveOutcome {
        // Refused rather than skipped: reporting "Saved" while the edit stays unwritten is how it got lost.
        if (state.allFilesBufferHasEdits()) {
            return ProjectSaveOutcome.Failed(
                message = "Switch to a single file to save — the All files view joins several files and " +
                    "cannot be written back to them.",
            )
        }

        state.stashOpenBufferInMemory()
        val ruleWrite = planRuleWrite(state = state, session = session)
        val workingCopyWrites = planWorkingCopyWrites(
            state = state,
            session = session,
            openPath = ruleWrite?.relativePath,
        )
        val schemaWrite = planSchemaWrite(state = state, session = session)
        val actionsWrite = planActionsWrite(state = state, session = session)

        val updatedSession = session.withActive { entry ->
            entry.copy(
                schemaLink = schemaWrite?.relativePath ?: entry.schemaLink,
                actionsLink = actionsWrite?.relativePath ?: entry.actionsLink,
                ruleFiles = mergeRuleFiles(existing = entry.ruleFiles, added = ruleWrite?.relativePath),
            )
        }

        val copiedContent = pendingCopies.associate { copy -> copy.target to copy.content }
        val onDisk = { path: Path -> copiedContent[path] ?: currentContent(path = path) }
        val plannedWrites = listOfNotNull(ruleWrite, schemaWrite, actionsWrite) + workingCopyWrites
        val (unchanged, contentWrites) = plannedWrites.partition { write -> onDisk(write.path) == write.content }

        contentWrites.forEach { write ->
            blockingConfirmation(write = write, onDisk = onDisk(write.path), approvals = approvals)
                ?.let { confirmation -> return ProjectSaveOutcome.NeedsConfirmation(request = confirmation) }
        }

        pendingCopies.forEach { copy -> copyIntoNewRoot(copy = copy) }
        contentWrites.forEach { write -> writeFile(write = write) }
        // Already what is on disk, so that is their baseline now; without it they stayed "unsaved".
        unchanged.forEach { write -> dirtyState.markClean(key = write.dirtyKey, content = write.content) }

        // The buffer now has a file behind it, so later saves update that file rather than creating
        // another one, and switching rule files knows what it is leaving.
        ruleWrite?.let { write -> state.selectedManifestRuleFile.value = write.relativePath }

        // Last, so an aborted save never leaves a manifest indexing files that were not written.
        val manifestWrite = planManifestWrite(session = updatedSession)
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
            ?: ProjectPaths.defaultRuleFile(
                entryId = session.entryId,
                entryCount = session.entries.size,
                fileName = defaultRuleFileName(ruleText = content),
            )

        val path = resolveRulePath(root = session.root, relativePath = relativePath)

        return PlannedWrite(
            kind = ProjectFileKind.RULE,
            relativePath = relativePath,
            path = path,
            content = content,
            dirtyKey = ProjectDirtyState.ruleKey(relativePath = relativePath),
        )
    }

    /**
     * The active entry's rule files whose working copy differs from their baseline — edited, then left
     * for another file. The open buffer is planned by [planRuleWrite] and skipped here.
     */
    private fun planWorkingCopyWrites(
        state: RuleEditorState,
        session: ProjectSession,
        openPath: String?,
    ): List<PlannedWrite> {
        val workingCopy = state.inMemoryRuleFiles.value
        return session.ruleFiles.filter { relativePath -> relativePath != openPath }.mapNotNull { relativePath ->
            val content = workingCopy[relativePath]
            val dirtyKey = ProjectDirtyState.ruleKey(relativePath = relativePath)
            val path = resolveRulePath(root = session.root, relativePath = relativePath)
            if (content == null || !dirtyState.isDirty(key = dirtyKey, content = content)) {
                null
            } else {
                PlannedWrite(
                    kind = ProjectFileKind.RULE,
                    relativePath = relativePath,
                    path = path,
                    content = content,
                    dirtyKey = dirtyKey,
                )
            }
        }
    }

    private fun planSchemaWrite(state: RuleEditorState, session: ProjectSession): PlannedWrite? {
        val content = state.schemaText.value
        if (content.isBlank() && session.schemaLink == null) return null

        val relativePath = session.schemaLink
            ?: ProjectPaths.defaultSchemaFile(entryId = session.entryId, entryCount = session.entries.size)

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
            ?: ProjectPaths.defaultActionsFile(entryId = session.entryId, entryCount = session.entries.size)

        return PlannedWrite(
            kind = ProjectFileKind.ACTIONS,
            relativePath = relativePath,
            path = ManifestPathResolver.resolveAllowingEscape(baseDir = session.root, relativePath = relativePath),
            content = content,
            dirtyKey = ProjectDirtyState.ACTIONS,
        )
    }

    private fun planManifestWrite(session: ProjectSession): PlannedWrite {
        val yaml = ManifestYamlBridge.toYaml(state = session.toEditorState())

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
     * Three cases: it leaves the project, the file moved on underneath us, or it replaces a file this
     * session never read. All would otherwise destroy something the user never saw.
     */
    private fun blockingConfirmation(
        write: PlannedWrite,
        onDisk: String?,
        approvals: ProjectSaveApprovals,
    ): ProjectSaveConfirmation? {
        val external = ProjectPaths.isExternal(relativePath = write.relativePath)
        if (external && !approvals.allowsExternalWrite(kind = write.kind)) {
            return ProjectSaveConfirmation.ExternalWrite(kind = write.kind, relativePath = write.relativePath)
        }

        if (onDisk == null) return null
        // No baseline means this session never read the file: one already there (another project's,
        // after Save As, or a second entry's default path) is not ours to replace unasked. An approved
        // external write has already been answered for that very file.
        val baseline = dirtyState.baseline(key = write.dirtyKey)
        val replacesUnread = baseline == null && !external && onDisk != write.content
        val changedUnderneath = baseline != null && onDisk != baseline
        if ((replacesUnread || changedUnderneath) && !approvals.allowsOverwrite(relativePath = write.relativePath)) {
            return ProjectSaveConfirmation.DiskConflict(kind = write.kind, relativePath = write.relativePath)
        }
        return null
    }

    private fun writeFile(write: PlannedWrite) {
        write.path.parent?.let { parent -> Files.createDirectories(parent) }
        AtomicFiles.writeString(path = write.path, content = write.content)
        dirtyState.markClean(key = write.dirtyKey, content = write.content)
    }

    private fun currentContent(path: Path): String? {
        return runCatching { Files.readString(path) }.getOrNull()
    }

    private fun resolveRulePath(root: Path, relativePath: String): Path {
        return ManifestPathResolver.resolveAllowingEscape(baseDir = root, relativePath = relativePath)
    }

    private fun mergeRuleFiles(existing: List<String>, added: String?): List<String> {
        if (added == null || added in existing) return existing
        return existing + added
    }

    /** Names a brand-new rule file after the first rule in it, which is what the user just wrote. */
    private fun defaultRuleFileName(ruleText: String): String {
        val ruleId = runCatching { Parser(input = ruleText).parseRules().firstOrNull()?.id }.getOrNull()
        val slug = ruleId?.takeIf { it.isNotBlank() }?.let { id -> ProjectPaths.slug(value = id) }
        return "${slug ?: "rules"}.rule"
    }
}
