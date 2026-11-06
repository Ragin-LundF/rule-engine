package ruleengine.intellij.manifest.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import ruleengine.intellij.manifest.ManifestFiles
import ruleengine.intellij.manifest.model.ManifestNode
import ruleengine.manifest.ManifestEntry
import javax.swing.tree.DefaultMutableTreeNode

/** Builds the tool window tree of one manifest: its entries with their schema, actions and rules. */
object ManifestTreeModel {
    fun build(project: Project, manifestFile: VirtualFile): DefaultMutableTreeNode? {
        val manifest = ManifestFiles.parse(project = project, file = manifestFile) ?: return null
        val root = DefaultMutableTreeNode(ManifestNode(label = manifest.name ?: manifestFile.name, file = manifestFile))
        manifest.entries.forEach { entry -> root.add(entryNode(manifestFile = manifestFile, entry = entry)) }
        return root
    }

    private fun entryNode(manifestFile: VirtualFile, entry: ManifestEntry): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(ManifestNode(label = "entry: ${entry.id}"))
        entry.schema?.let { path ->
            node.add(fileNode(manifestFile = manifestFile, label = "schema: $path", path = path))
        }
        entry.actions?.let { path ->
            node.add(fileNode(manifestFile = manifestFile, label = "actions: $path", path = path))
        }
        val rules = DefaultMutableTreeNode(ManifestNode(label = "rules (${entry.rules.size})"))
        entry.rules.forEach { path ->
            rules.add(fileNode(manifestFile = manifestFile, label = path, path = path, isRule = true))
        }
        node.add(rules)
        return node
    }

    private fun fileNode(
        manifestFile: VirtualFile,
        label: String,
        path: String,
        isRule: Boolean = false,
    ): DefaultMutableTreeNode {
        val file = ManifestFiles.resolve(manifestFile = manifestFile, relativePath = path)
        val shown = if (file == null) "$label (missing)" else label
        return DefaultMutableTreeNode(ManifestNode(label = shown, file = file, isRule = isRule))
    }
}
