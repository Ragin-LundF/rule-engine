package ui.project

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ManifestPathResolver
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.manifest.ManifestYamlBridge
import ui.manifest.model.ManifestEditorState
import ui.manifest.model.ManifestPathKind
import ui.pickActionsFilePath
import ui.pickProjectManifestPath
import ui.pickProjectManifestSavePath
import ui.pickRuleFilePath
import ui.pickSchemaFilePath
import ui.pickSharedFileSavePath
import ui.project.io.ProjectLoader
import ui.project.io.ProjectSaver
import ui.project.manifest.ManifestEntryRemoval
import ui.project.manifest.toEditorState
import ui.project.manifest.toProjectEntries
import ui.project.model.ProjectEntry
import ui.project.model.ProjectFileKind
import ui.project.model.ProjectSession
import ui.project.model.dialog.PendingProjectAction
import ui.project.model.dialog.ProjectDialog
import ui.project.model.dialog.ProjectSaveApprovals
import ui.project.model.dialog.ProjectSaveConfirmation
import ui.project.model.io.ProjectLoadResult
import ui.project.model.io.ProjectSaveOutcome
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives the project workflow: open, new, save, save as, and linking shared schema and action files.
 *
 * Everything the toolbar and the area screens call lands here, so the composables stay free of file
 * handling and the sequencing that makes it safe — check for unsaved work, ask, then act. The
 * questions it needs to ask are exposed as [dialog] rather than opened directly, keeping this class
 * testable and the dialogs in the composable layer where they belong.
 */
@Suppress("TooManyFunctions")
// 37 methods against a threshold of 20. Splitting was considered and rejected: they are not 37
// independent operations but one state machine over `session` / `dialog` / `dirtyState`, and most of
// them are the *resumption* half of a guarded action — `openProject` asks, `onUnsavedChangesSave`
// answers, `resume` continues. Carving those into collaborators would spread three mutable fields
// across objects and make the ask-then-act sequencing, which is the whole point of this class,
// something you have to reassemble from several files to check.
class ProjectWorkspace(
    private val state: RuleEditorState,
    // Injected so the open/save sequencing can be tested without a native dialog on screen.
    private val chooseManifestToOpen: () -> Path? = { pickProjectManifestPath() },
    private val chooseManifestToSave: (String) -> Path? = { suggested ->
        pickProjectManifestSavePath(suggestedName = suggested)
    },
    /** Same reason: a manifest path can be chosen in a test without a dialog appearing. */
    private val chooseFileOfKind: (ManifestPathKind) -> Path? = { kind ->
        when (kind) {
            ManifestPathKind.SCHEMA -> pickSchemaFilePath()
            ManifestPathKind.ACTIONS -> pickActionsFilePath()
            ManifestPathKind.RULE -> pickRuleFilePath()
        }
    },
) {

    private val dirtyState = ProjectDirtyState()
    private val loader = ProjectLoader(dirtyState = dirtyState)
    private val saver = ProjectSaver(dirtyState = dirtyState)

    val session: MutableState<ProjectSession?> = mutableStateOf(value = null)
    val dialog: MutableState<ProjectDialog?> = mutableStateOf(value = null)
    val closeRequested: MutableState<Boolean> = mutableStateOf(value = false)

    /**
     * Bumped whenever a baseline moves. [ProjectDirtyState] is a plain map, so without this the
     * toolbar would keep showing "unsaved" after a save until something else recomposed it.
     */
    private val revision: MutableState<Int> = mutableStateOf(value = 0)

    private var approvals: ProjectSaveApprovals = ProjectSaveApprovals()
    private var retrySave: (() -> ProjectSaveOutcome)? = null
    private var resumeAfterSave: PendingProjectAction? = null

    /** Links picked before the project has a root; turned into relative paths at the first save. */
    private var scratchSchemaLink: Path? = null
    private var scratchActionsLink: Path? = null

    /**
     * True while the buffers hold a sample rather than a project's own files.
     *
     * Provenance, not a mode: [loadSample] already clears [session], so on its own this changes
     * nothing. It exists so that a session appearing from anywhere else cannot become the save target
     * for buffers that never came out of it — which is precisely the bug that made loading a sample
     * over an open project write the sample into that project's directory.
     */
    private var buffersFromSample: Boolean = false

    val isDirty: Boolean
        get() {
            revision.value // read so composition re-runs when baselines change
            return dirtyState.isDirty(key = ProjectDirtyState.SCHEMA, content = state.schemaText.value) ||
                    dirtyState.isDirty(key = ProjectDirtyState.ACTIONS, content = state.actionSchemaText.value) ||
                    dirtyState.isDirty(key = openRuleKey(), content = state.ruleValue.value.text)
        }

    // ── Open / New ────────────────────────────────────────────────────────────

    fun openProject() = guardUnsavedWork(action = PendingProjectAction.OpenProject) { performOpen() }

    fun newProject() = guardUnsavedWork(action = PendingProjectAction.NewProject) { performNew() }

    /**
     * Opens without asking about unsaved work.
     *
     * Separate from [openProject] because the answer has already been given by the time this runs —
     * routing "Discard" back through the guard would just raise the same question again, which reads
     * as a button that does nothing.
     */
    private fun performOpen() {
        val manifestPath = chooseManifestToOpen() ?: run {
            state.setStatus(msg = "Open cancelled", kind = StatusKind.IDLE)
            return
        }
        when (val result = loader.load(manifestPath = manifestPath, into = state)) {
            is ProjectLoadResult.Loaded -> {
                session.value = result.session
                scratchSchemaLink = null
                scratchActionsLink = null
                buffersFromSample = false
                revision.value++
                reportOpened(session = result.session)
            }

            is ProjectLoadResult.Failed -> {
                // Nothing was cleared, so the project already open is still intact.
                dialog.value = ProjectDialog.Error(title = "Cannot open project", message = result.message)
            }
        }
    }

    private fun performNew() {
        state.reset()
        dirtyState.clear()
        session.value = null
        scratchSchemaLink = null
        scratchActionsLink = null
        buffersFromSample = false
        revision.value++
        state.setStatus(msg = "New project", kind = StatusKind.IDLE)
    }

    /**
     * Loads a sample into the editor, as the scratch project it is.
     *
     * A sample has no location on disk and therefore no session: [ProjectSession.root] is a real
     * `Path`, and inventing one would make every session-derived surface — the entry picker, the
     * manifest area, the linked-file chips, the status bar, and above all the save target — describe a
     * directory the sample never came from. That is what used to happen: the sample path wrote only
     * `RuleEditorState`, so a sample loaded over an open project left the session, and the dirty
     * baselines, describing the *previous* project. The picker named its entry, clicking that entry
     * reloaded its files over the sample, and Save wrote the sample's schema and actions into its
     * directory.
     *
     * Takes the editor write as a lambda rather than taking the sample itself: `applySample` lives in
     * `ui.workbench.areas`, which depends on this package, so reaching for it here would invert the
     * layering. Running it *inside* this method is what stops a caller clearing without applying, or
     * applying without clearing.
     *
     * [applyToEditor] runs last so the status it sets is the one the user is left with.
     */
    fun loadSample(applyToEditor: () -> Unit) {
        session.value = null
        dirtyState.clear()
        scratchSchemaLink = null
        scratchActionsLink = null
        buffersFromSample = true
        applyToEditor()
        revision.value++
    }

    fun requestClose() {
        if (!isDirty) {
            closeRequested.value = true
            return
        }
        dialog.value = ProjectDialog.UnsavedChanges(
            projectName = session.value?.displayName ?: "This project",
            pending = PendingProjectAction.CloseWindow,
        )
    }

    // ── Manifest entries ──────────────────────────────────────────────────────

    /**
     * Makes [entryId] the entry the editor is working on.
     *
     * Guarded like an open, because it replaces every buffer: the schema, the actions and the rule
     * files all belong to the entry being left behind.
     */
    fun selectEntry(entryId: String) {
        val current = session.value ?: return
        if (current.activeEntryId == entryId || current.entry(id = entryId) == null) return
        guardUnsavedWork(action = PendingProjectAction.SwitchEntry(entryId = entryId)) {
            performSelectEntry(entryId = entryId)
        }
    }

    private fun performSelectEntry(entryId: String) {
        val current = session.value ?: return
        val missing = loader.activate(session = current, entryId = entryId, into = state)
        session.value = current.copy(activeEntryId = entryId, missingFiles = missing)
        revision.value++
        state.setStatus(msg = "Entry $entryId", kind = StatusKind.IDLE)
    }

    /**
     * Adds an empty entry and switches to it.
     *
     * Nothing is written: the entry exists in the manifest with an id and no files until the user
     * gives it a schema, actions or rules and saves. Creating placeholder files here would litter the
     * project with empty YAML for an entry the user may yet abandon.
     */
    fun addEntry(entryId: String): Boolean {
        val current = session.value ?: run {
            dialog.value = ProjectDialog.Error(
                title = "No project",
                message = "Save this project before adding manifest entries.",
            )
            return false
        }
        val trimmed = entryId.trim()
        if (trimmed.isBlank() || current.entry(id = trimmed) != null) {
            val reason = if (trimmed.isBlank()) {
                "An entry needs a name."
            } else {
                "An entry named $trimmed already exists."
            }
            dialog.value = ProjectDialog.Error(title = "Cannot add entry", message = reason)
            return false
        }

        val added = current.copy(entries = current.entries + ProjectEntry(id = trimmed))
        session.value = added
        syncManifestBuffers()
        performSelectEntry(entryId = trimmed)
        return true
    }

    /** A name no existing entry uses, so "+ Add entry" never has to fail on the first click. */
    fun suggestEntryId(): String {
        val existing = session.value?.entries?.map { it.id }.orEmpty().toSet()
        return generateSequence(seed = existing.size + 1) { it + 1 }
            .map { index -> "entry-$index" }
            .first { candidate -> candidate !in existing }
    }

    fun requestRemoveEntry(entryId: String) {
        val current = session.value ?: return
        if (current.entries.size <= 1) {
            dialog.value = ProjectDialog.Error(
                title = "Cannot remove entry",
                message = "A manifest needs at least one entry.",
            )
            return
        }
        if (current.entry(id = entryId) == null) return

        dialog.value = ProjectDialog.RemoveEntry(
            entryId = entryId,
            deletable = ManifestEntryRemoval.deletableFiles(session = current, entryId = entryId),
            shared = ManifestEntryRemoval.keptFiles(session = current, entryId = entryId),
        )
    }

    fun onRemoveEntryDeletingFiles(entryId: String) {
        val current = session.value ?: return
        val deletable = ManifestEntryRemoval.deletableFiles(session = current, entryId = entryId)
        val failures = ManifestEntryRemoval.delete(files = deletable)
        deletable.forEach { file ->
            dirtyState.forget(key = ProjectDirtyState.ruleKey(relativePath = file.relativePath))
        }

        removeEntry(entryId = entryId)
        if (failures.isEmpty()) {
            state.setStatus(msg = "Removed $entryId — ${deletable.size} file(s) deleted", kind = StatusKind.SUCCESS)
            return
        }
        // The entry is gone either way; saying which files survived beats a silent partial delete.
        dialog.value = ProjectDialog.Error(
            title = "Some files were not deleted",
            message = failures.joinToString(separator = "\n"),
        )
    }

    fun onRemoveEntryKeepingFiles(entryId: String) {
        removeEntry(entryId = entryId)
        state.setStatus(msg = "Removed $entryId from the manifest — files kept", kind = StatusKind.SUCCESS)
    }

    private fun removeEntry(entryId: String) {
        dialog.value = null
        val current = session.value ?: return
        val remaining = current.entries.filterNot { it.id == entryId }
        if (remaining.isEmpty()) return

        val reduced = current.copy(entries = remaining, activeEntryId = current.activeEntryId)
        session.value = reduced
        syncManifestBuffers()

        if (current.activeEntryId == entryId) performSelectEntry(entryId = remaining.first().id)

        // Files may have just been deleted, so the index must stop naming them now, not at next save.
        session.value?.let { updated -> saver.saveManifest(session = updated) }
        revision.value++
    }

    /**
     * Applies what the Manifest area edited back onto the session.
     *
     * The session is the source of truth; without this the saver would regenerate the manifest from
     * the session and silently throw away everything typed in that area.
     */
    fun applyManifestEditorState(edited: ManifestEditorState) {
        val current = session.value ?: return
        val entries = edited.toProjectEntries().filter { it.id.isNotBlank() }
        if (entries.isEmpty()) return

        val activeBefore = current.active
        val active = entries.firstOrNull { it.id == current.activeEntryId } ?: entries.first()
        val updated = current.copy(
            entries = entries,
            activeEntryId = active.id,
            manifestName = edited.name.takeIf { it.isNotBlank() },
        )
        if (updated == current) return

        session.value = updated
        syncManifestBuffers()

        // Only a change to what the active entry points at can invalidate the buffers on screen.
        val pathsChanged = active.schemaLink != activeBefore.schemaLink ||
                active.actionsLink != activeBefore.actionsLink ||
                active.ruleFiles != activeBefore.ruleFiles
        if (pathsChanged) {
            performSelectEntry(entryId = active.id)
        } else {
            // A rename changes the id and nothing else, so there is nothing to reload — but the editor's
            // own entry selection still has to follow. Without this it keeps pointing at an id the
            // manifest no longer has, `currentEntryRulePaths()` finds nothing, and the next
            // `loadRuleFiles` replaces every rule file in the working copy with an empty map.
            adoptActiveEntry(entryId = active.id)
            revision.value++
        }
    }

    /**
     * Points the editor's own entry selection at [entryId], without reloading anything.
     *
     * The third store. `session.activeEntryId` is the save target,
     * `RuleEditorState.selectedManifestEntry` is what every read of the parsed manifest goes through,
     * and nothing observes them into agreement — so a change of active entry that does *not* go through
     * [performSelectEntry] has to come through here instead.
     *
     * Deliberately not a reload: a rename leaves every path alone, and reloading would throw away the
     * in-memory edits that are the whole point of the working copy.
     */
    private fun adoptActiveEntry(entryId: String) {
        state.selectedManifestEntry.value = entryId
    }

    /**
     * Regenerates the manifest buffers from the session.
     *
     * [RuleEditorState.parsedManifest] still drives the rule-file picker, the rule tree and the
     * diagrams, so it has to follow the session rather than drift into being a second source of truth
     * that the saver would overrule.
     */
    private fun syncManifestBuffers() {
        val current = session.value
        val yaml = current?.let { ManifestYamlBridge.toYaml(state = it.toEditorState()) }.orEmpty()
        state.manifestText.value = yaml
        state.manifestFieldValue.value = TextFieldValue(text = yaml)
        state.parsedManifest.value = runCatching { ManifestLoader.loadFromString(content = yaml) }.getOrNull()
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveProject(): Boolean {
        val target = saveTargetSession() ?: createScratchSession() ?: return false
        return runSave { saver.save(state = state, session = target, approvals = approvals) }
    }

    fun saveProjectAs(): Boolean {
        val current = saveTargetSession() ?: return saveProject()
        val manifestPath = chooseManifestToSave(current.manifestFileName) ?: run {
            state.setStatus(msg = "Save As cancelled", kind = StatusKind.IDLE)
            return false
        }
        return runSave {
            saver.saveAs(
                state = state,
                session = current,
                newManifestPath = manifestPath,
                approvals = approvals,
            )
        }
    }

    /**
     * The session a save may write to, or null when it must ask for a location instead.
     *
     * Falling through to [createScratchSession] rather than refusing: the requirement is only that
     * sample buffers can never be written into a session's files, and an error dialog would satisfy it
     * by leaving the user holding work with nowhere to put it. Asking where to save is the thing they
     * were trying to do anyway, and the old project is equally untouchable either way.
     */
    private fun saveTargetSession(): ProjectSession? {
        return session.value?.takeUnless { buffersFromSample }
    }

    private fun createScratchSession(): ProjectSession? {
        val manifestPath = chooseManifestToSave(ProjectPaths.DEFAULT_MANIFEST_FILE)
            ?.toAbsolutePath()?.normalize() ?: run {
            state.setStatus(msg = "Save cancelled", kind = StatusKind.IDLE)
            return null
        }
        val root = manifestPath.parent ?: return null

        return ProjectSession.singleEntry(
            root = root,
            manifestFileName = manifestPath.fileName.toString(),
            entry = ProjectEntry(
                id = state.parsedManifest.value?.entries?.firstOrNull()?.id ?: ProjectSession.DEFAULT_ENTRY_ID,
                schemaLink = scratchSchemaLink?.let { ProjectPaths.relativize(root = root, target = it) },
                actionsLink = scratchActionsLink?.let { ProjectPaths.relativize(root = root, target = it) },
            ),
            manifestName = state.parsedManifest.value?.name,
        )
    }

    private fun runSave(attempt: () -> ProjectSaveOutcome): Boolean {
        return when (val outcome = attempt()) {
            is ProjectSaveOutcome.Saved -> {
                session.value = outcome.session
                // The manifest on disk was just regenerated from the session; the buffer has to agree.
                syncManifestBuffers()
                // A scratch project's first save invents an entry id. Nothing had ever set the editor's
                // selection, so it stayed null and every read of the parsed manifest missed.
                adoptActiveEntry(entryId = outcome.session.activeEntryId)
                scratchSchemaLink = null
                scratchActionsLink = null
                // The files just written are where the buffers live now, so their provenance is this
                // session rather than the sample they were loaded from.
                buffersFromSample = false
                approvals = ProjectSaveApprovals()
                retrySave = null
                revision.value++
                state.setStatus(
                    msg = "Saved ${outcome.session.displayName} — ${outcome.filesWritten} file(s) written",
                    kind = StatusKind.SUCCESS,
                )
                resumeAfterSave?.let { pending ->
                    resumeAfterSave = null
                    resume(action = pending)
                }
                true
            }

            is ProjectSaveOutcome.NeedsConfirmation -> {
                retrySave = attempt
                dialog.value = when (val request = outcome.request) {
                    is ProjectSaveConfirmation.ExternalWrite -> ProjectDialog.ExternalWrite(request = request)
                    is ProjectSaveConfirmation.DiskConflict -> ProjectDialog.DiskConflict(request = request)
                }
                false
            }

            is ProjectSaveOutcome.Failed -> {
                retrySave = null
                resumeAfterSave = null
                dialog.value = ProjectDialog.Error(title = "Save failed", message = outcome.message)
                false
            }
        }
    }

    // ── Dialog answers ────────────────────────────────────────────────────────

    fun dismissDialog() {
        dialog.value = null
        retrySave = null
        resumeAfterSave = null
    }

    fun onUnsavedChangesSave(pending: PendingProjectAction) {
        dialog.value = null
        resumeAfterSave = pending
        if (!saveProject()) {
            // Cancelled or waiting on a confirmation: the pending action must not run yet.
            if (dialog.value == null) resumeAfterSave = null
        }
    }

    fun onUnsavedChangesDiscard(pending: PendingProjectAction) {
        dialog.value = null
        // Baselines are left alone: the resumed action bypasses the dirty check anyway, and clearing
        // them would mark the buffers "never saved" — still dirty — if the user then cancels the
        // file dialog and stays where they are.
        resume(action = pending)
    }

    fun onApproveExternalWrite(kind: ProjectFileKind) {
        approvals = approvals.copy(externalWrites = approvals.externalWrites + kind)
        dialog.value = null
        retrySave?.let { attempt -> runSave(attempt = attempt) }
    }

    /** "Copy into project" — stop sharing and keep the edit local instead of changing the shared file. */
    fun onCopyExternalIntoProject(kind: ProjectFileKind) {
        dialog.value = null
        val current = session.value ?: return
        session.value = when (kind) {
            ProjectFileKind.SCHEMA -> current.withActive { entry ->
                entry.copy(
                    schemaLink = ProjectPaths.defaultSchemaFile(
                        entryId = entry.id,
                        entryCount = current.entries.size,
                    ),
                )
            }

            ProjectFileKind.ACTIONS -> current.withActive { entry ->
                entry.copy(
                    actionsLink = ProjectPaths.defaultActionsFile(
                        entryId = entry.id,
                        entryCount = current.entries.size,
                    ),
                )
            }

            ProjectFileKind.RULE -> current
        }
        retrySave = null
        saveProject()
    }

    fun onOverwriteConflict(relativePath: String) {
        approvals = approvals.copy(overwrittenConflicts = approvals.overwrittenConflicts + relativePath)
        dialog.value = null
        retrySave?.let { attempt -> runSave(attempt = attempt) }
    }

    fun onReloadConflict() {
        dialog.value = null
        retrySave = null
        val current = session.value ?: return
        loader.load(manifestPath = current.manifestPath, into = state)
        revision.value++
        state.setStatus(msg = "Reloaded from disk", kind = StatusKind.IDLE)
    }

    fun onLinkAfterExport(kind: ProjectFileKind, exported: Path) {
        dialog.value = null
        applyLink(kind = kind, path = exported)
    }

    /** Runs the interrupted action now that the unsaved-work question has been answered. */
    private fun resume(action: PendingProjectAction) {
        when (action) {
            PendingProjectAction.OpenProject -> performOpen()
            PendingProjectAction.NewProject -> performNew()
            PendingProjectAction.CloseWindow -> closeRequested.value = true
            is PendingProjectAction.SwitchEntry -> performSelectEntry(entryId = action.entryId)
        }
    }

    private inline fun guardUnsavedWork(action: PendingProjectAction, body: () -> Unit) {
        if (isDirty) {
            dialog.value = ProjectDialog.UnsavedChanges(
                projectName = session.value?.displayName ?: "This project",
                pending = action,
            )
            return
        }
        body()
    }

    // ── Choosing a path to write into the manifest ────────────────────────────

    /**
     * Why the manifest's `Choose…` buttons cannot be used, or null when they can.
     *
     * **A manifest path is relative to the manifest file.** A project that has never been saved has no
     * manifest file, so there is no location for a chosen path to be relative to — and writing the
     * absolute path instead would produce an entry that stops resolving the moment the project moves,
     * which is worse than not offering the dialog.
     */
    val chosenPathBlockedReason: String?
        get() = if (session.value == null) {
            "Save the project first — a path in the manifest is relative to the manifest file."
        } else {
            null
        }

    /**
     * The file the user picks, as the manifest would write it: relative to the project root.
     *
     * Returns null when the dialog is cancelled, and refuses outright while
     * [chosenPathBlockedReason] holds — the button is disabled for that case, and a guard here means a
     * caller that ignores the reason cannot write an unresolvable path anyway.
     */
    fun choosePathForManifest(kind: ManifestPathKind): String? {
        val root = session.value?.root ?: return null
        val picked = chooseFileOfKind(kind) ?: return null
        return ProjectPaths.relativize(root = root, target = picked)
    }

    // ── Linking shared schema / action files ──────────────────────────────────

    fun linkSchema() {
        val path = pickSchemaFilePath() ?: return
        applyLink(kind = ProjectFileKind.SCHEMA, path = path)
    }

    fun linkActions() {
        val path = pickActionsFilePath() ?: return
        applyLink(kind = ProjectFileKind.ACTIONS, path = path)
    }

    /**
     * Points the project at [path] and loads its content.
     *
     * The file is linked, never copied: sharing one schema between projects is the whole reason this
     * exists, and a copy would drift the moment either side is edited.
     */
    private fun applyLink(kind: ProjectFileKind, path: Path) {
        val content = runCatching { Files.readString(path) }.getOrElse { ex ->
            dialog.value = ProjectDialog.Error(title = "Cannot read file", message = ex.message ?: "unreadable")
            return
        }

        when (kind) {
            ProjectFileKind.SCHEMA -> {
                state.schemaText.value = content
                state.schemaFieldValue.value = TextFieldValue(text = content)
                state.parsedSchema.value = runCatching {
                    FieldSchemaLoader.loadFromString(content = content, nameHint = path.fileName.toString())
                }.getOrNull()
                dirtyState.markClean(key = ProjectDirtyState.SCHEMA, content = content)
            }

            ProjectFileKind.ACTIONS -> {
                state.actionSchemaText.value = content
                state.actionFieldValue.value = TextFieldValue(text = content)
                state.parsedActionSchema.value = runCatching {
                    ActionSchemaLoader.loadFromString(content = content)
                }.getOrNull()
                dirtyState.markClean(key = ProjectDirtyState.ACTIONS, content = content)
            }

            ProjectFileKind.RULE -> return
        }

        val current = session.value
        if (current == null) {
            // Scratch: remember where it came from so the first save links rather than copies.
            if (kind == ProjectFileKind.SCHEMA) scratchSchemaLink = path else scratchActionsLink = path
        } else {
            val relative = ProjectPaths.relativize(root = current.root, target = path)
            session.value = current.withActive { entry ->
                if (kind == ProjectFileKind.SCHEMA) {
                    entry.copy(schemaLink = relative)
                } else {
                    entry.copy(actionsLink = relative)
                }
            }
            syncManifestBuffers()
        }

        revision.value++
        state.setStatus(msg = "Linked ${path.fileName}", kind = StatusKind.SUCCESS)
    }

    fun unlink(kind: ProjectFileKind) {
        when (kind) {
            ProjectFileKind.SCHEMA -> {
                state.schemaText.value = ""
                state.schemaFieldValue.value = TextFieldValue(text = "")
                state.parsedSchema.value = null
                dirtyState.forget(key = ProjectDirtyState.SCHEMA)
                scratchSchemaLink = null
                session.value = session.value?.withActive { entry -> entry.copy(schemaLink = null) }
            }

            ProjectFileKind.ACTIONS -> {
                state.actionSchemaText.value = ""
                state.actionFieldValue.value = TextFieldValue(text = "")
                state.parsedActionSchema.value = null
                dirtyState.forget(key = ProjectDirtyState.ACTIONS)
                scratchActionsLink = null
                session.value = session.value?.withActive { entry -> entry.copy(actionsLink = null) }
            }

            ProjectFileKind.RULE -> return
        }
        syncManifestBuffers()
        revision.value++
        state.setStatus(msg = "Unlinked ${kind.label}", kind = StatusKind.IDLE)
    }

    /** Writes the schema or actions somewhere shared, then offers to link the project to it. */
    fun exportShared(kind: ProjectFileKind) {
        val content = if (kind == ProjectFileKind.SCHEMA) state.schemaText.value else state.actionSchemaText.value
        if (content.isBlank()) {
            state.setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
            return
        }

        val suggested = if (kind == ProjectFileKind.SCHEMA) {
            ProjectPaths.DEFAULT_SCHEMA_FILE
        } else {
            ProjectPaths.DEFAULT_ACTIONS_FILE
        }
        val target = pickSharedFileSavePath(title = "Save ${kind.label} YAML", suggestedName = suggested) ?: run {
            state.setStatus(msg = "Save cancelled", kind = StatusKind.IDLE)
            return
        }

        runCatching { Files.writeString(target, content) }.onFailure { ex ->
            dialog.value = ProjectDialog.Error(title = "Save failed", message = ex.message ?: "could not write")
            return
        }

        state.setStatus(msg = "Saved ${target.fileName}", kind = StatusKind.SUCCESS)
        if (target != linkedPath(kind = kind)) {
            dialog.value = ProjectDialog.LinkAfterExport(kind = kind, exported = target)
        }
    }

    private fun linkedPath(kind: ProjectFileKind): Path? {
        val current = session.value ?: return null
        val link = if (kind == ProjectFileKind.SCHEMA) current.schemaLink else current.actionsLink
        return link?.let { ManifestPathResolver.resolveAllowingEscape(baseDir = current.root, relativePath = it) }
    }

    /** Says plainly what opened, including what could not be found, rather than a flat "loaded". */
    private fun reportOpened(session: ProjectSession) {
        val missing = session.missingFiles
        if (missing.isEmpty()) {
            state.setStatus(
                msg = "Opened ${session.displayName} — ${session.ruleFiles.size} rule file(s)",
                kind = StatusKind.SUCCESS,
            )
            return
        }
        state.setStatus(
            msg = "Opened ${session.displayName} — ${missing.size} referenced file(s) missing: " +
                    missing.joinToString(separator = ", ") { it.relativePath },
            kind = StatusKind.ERROR,
        )
    }

    private fun openRuleKey(): String =
        ProjectDirtyState.ruleKey(relativePath = state.selectedManifestRuleFile.value ?: "")
}
