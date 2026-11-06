package ruleengine.intellij.manifest

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

/**
 * Finds the manifests that list a rule by looking in the rule's folder and its parents.
 *
 * Stops at the project's content root, so it never scans outside the project. Only YAML files with a
 * top-level `entries:` key are parsed, and the rules they list are never read.
 */
object ManifestDetector {
    fun manifestsListing(project: Project, rule: VirtualFile): List<VirtualFile> {
        val root = ProjectFileIndex.getInstance(project).getContentRootForFile(rule)
        return generateSequence(rule.parent) { dir -> if (dir == root) null else dir.parent }
            .flatMap { dir -> dir.children.asSequence() }
            .filter { candidate -> lists(project = project, manifestFile = candidate, rule = rule) }
            .toList()
    }

    fun lists(project: Project, manifestFile: VirtualFile, rule: VirtualFile): Boolean {
        val manifest = ManifestFiles.parse(project = project, file = manifestFile) ?: return false
        return ManifestFiles.entryListing(manifestFile = manifestFile, manifest = manifest, rule = rule) != null
    }
}
