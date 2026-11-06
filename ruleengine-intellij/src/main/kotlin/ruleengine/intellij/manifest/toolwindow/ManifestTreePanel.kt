package ruleengine.intellij.manifest.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import ruleengine.intellij.lang.RuleFileType
import ruleengine.intellij.manifest.ManifestFiles
import ruleengine.intellij.manifest.ManifestLinks
import ruleengine.intellij.manifest.model.ManifestNode
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Shows the manifest of the file in the editor: the manifest itself, or the one a rule is linked to.
 *
 * Double-click opens a file. Opening a rule from here links it to the shown manifest, which is how a
 * rule listed by several manifests gets the right one.
 */
class ManifestTreePanel(private val project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()) {
    private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode(ManifestNode(label = EMPTY_LABEL))))
    private var shownManifest: VirtualFile? = null

    init {
        add(JBScrollPane(tree), BorderLayout.CENTER)
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                return openSelected()
            }
        }.installOn(tree)
        project.messageBus.connect(parentDisposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    event.newFile?.let { file -> show(file = file) }
                }
            },
        )
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let { file -> show(file = file) }
    }

    private fun show(file: VirtualFile) {
        val root = runReadAction {
            val manifest = manifestOf(file = file) ?: return@runReadAction null
            shownManifest = manifest
            ManifestTreeModel.build(project = project, manifestFile = manifest)
        } ?: return
        tree.model = DefaultTreeModel(root)
        TreeUtil.expandAll(tree)
    }

    /** Other files, such as a schema, keep the current tree. */
    private fun manifestOf(file: VirtualFile): VirtualFile? {
        return when {
            file.fileType == RuleFileType -> ManifestLinks.getInstance(project).manifestFor(rule = file)
            ManifestFiles.isManifest(project = project, file = file) -> file
            else -> null
        }
    }

    private fun openSelected(): Boolean {
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val node = selected?.userObject as? ManifestNode ?: return false
        val file = node.file ?: return false
        val manifest = shownManifest
        if (node.isRule && manifest != null) {
            ManifestLinks.getInstance(project).link(rule = file, manifest = manifest)
        }
        FileEditorManager.getInstance(project).openFile(file, true)
        return true
    }

    private companion object {
        const val EMPTY_LABEL = "Open a manifest or a rule file"
    }
}
