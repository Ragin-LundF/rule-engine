package ruleengine.intellij.manifest

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.EditorNotifications
import ruleengine.intellij.manifest.model.ManifestLinkState

/**
 * Remembers which manifest a rule belongs to, per project and per user (workspace file).
 *
 * A rule without a stored link is linked automatically when exactly one manifest in its folder or a
 * parent folder lists it.
 */
@Service(Service.Level.PROJECT)
@State(name = "RuleManifestLinks", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ManifestLinks(private val project: Project) : PersistentStateComponent<ManifestLinkState> {
    private var state = ManifestLinkState()

    /** Changes whenever a link changes, so cached rule sources are rebuilt. */
    val tracker = SimpleModificationTracker()

    override fun getState(): ManifestLinkState {
        return state
    }

    override fun loadState(state: ManifestLinkState) {
        this.state = state
    }

    /** The stored manifest, if it still exists and still lists the rule; otherwise a detected one. */
    fun manifestFor(rule: VirtualFile): VirtualFile? {
        val stored = state.links[rule.url]?.let { url -> VirtualFileManager.getInstance().findFileByUrl(url) }
        if (stored != null && ManifestDetector.lists(project = project, manifestFile = stored, rule = rule)) {
            return stored
        }
        val detected = ManifestDetector.manifestsListing(project = project, rule = rule).singleOrNull() ?: return null
        store(rule = rule, manifest = detected)
        return detected
    }

    fun link(rule: VirtualFile, manifest: VirtualFile) {
        store(rule = rule, manifest = manifest)
        EditorNotifications.getInstance(project).updateNotifications(rule)
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    // Synchronized: detection stores links from background highlighting threads too.
    @Synchronized
    private fun store(rule: VirtualFile, manifest: VirtualFile) {
        if (state.links.put(rule.url, manifest.url) != manifest.url) {
            tracker.incModificationCount()
        }
    }

    companion object {
        fun getInstance(project: Project): ManifestLinks {
            return project.service()
        }
    }
}
