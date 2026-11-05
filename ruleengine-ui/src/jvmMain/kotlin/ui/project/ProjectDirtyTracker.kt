package ui.project

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ui.editor.rules.RuleEditorState
import ui.project.model.ProjectSession

/**
 * Answers "is there unsaved work?" for the open project: edited buffers against their baselines in
 * [dirtyState], and the session against the manifest on disk.
 */
class ProjectDirtyTracker(
    private val state: RuleEditorState,
    private val dirtyState: ProjectDirtyState,
    private val session: State<ProjectSession?>,
) {

    /**
     * Bumped whenever a baseline moves. [ProjectDirtyState] is a plain map, so without this the
     * toolbar would keep showing "unsaved" after a save until something else recomposed it.
     */
    private val revision: MutableState<Int> = mutableStateOf(value = 0)

    /**
     * The session as the manifest on disk describes it: set on open, save and entry removal. An entry
     * added, renamed or relinked since is unsaved work as much as an edited file is.
     */
    private var savedSession: ProjectSession? = null

    val isDirty: Boolean
        get() {
            return buffersDirty() || manifestDirty()
        }

    fun baselinesChanged() {
        revision.value++
    }

    /** Null when no manifest is on disk for the buffers: a new project or a sample. */
    fun manifestSaved(session: ProjectSession?) {
        savedSession = session
    }

    fun buffersDirty(): Boolean {
        revision.value // read so composition re-runs when baselines change
        return dirtyState.isDirty(key = ProjectDirtyState.SCHEMA, content = state.schemaText.value) ||
                dirtyState.isDirty(key = ProjectDirtyState.ACTIONS, content = state.actionSchemaText.value) ||
                ruleFilesDirty()
    }

    /**
     * The buffer on screen, and every other rule file of the working copy — a file edited and then
     * left for another one is still unsaved.
     */
    private fun ruleFilesDirty(): Boolean {
        val showAll = state.showAllRules.value
        val bufferDirty = if (showAll) {
            state.allFilesBufferHasEdits()
        } else {
            dirtyState.isDirty(key = openRuleKey(), content = state.ruleValue.value.text)
        }
        // In All files the working copy is the truth for every file; otherwise the buffer is for the open one.
        val openPath = state.selectedManifestRuleFile.value.takeUnless { showAll }
        return bufferDirty || state.inMemoryRuleFiles.value.any { (relativePath, content) ->
            relativePath != openPath &&
                dirtyState.isDirty(key = ProjectDirtyState.ruleKey(relativePath = relativePath), content = content)
        }
    }

    private fun manifestDirty(): Boolean {
        val current = session.value
        val saved = savedSession
        return current?.entries != saved?.entries || current?.manifestName != saved?.manifestName
    }

    private fun openRuleKey(): String {
        return ProjectDirtyState.ruleKey(relativePath = state.selectedManifestRuleFile.value ?: "")
    }
}
