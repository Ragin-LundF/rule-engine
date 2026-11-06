package ruleengine.intellij.manifest

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import ruleengine.intellij.yaml.RuleYamlKinds
import ruleengine.intellij.yaml.model.RuleYamlKind
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ProjectManifest
import java.nio.file.Path

/** Reads manifest files and resolves the paths they name, without touching the rules they list. */
object ManifestFiles {
    private val YAML_EXTENSIONS = setOf("yaml", "yml")

    fun isYaml(file: VirtualFile): Boolean {
        return !file.isDirectory && file.extension?.lowercase() in YAML_EXTENSIONS
    }

    /** A manifest is the YAML file with a top-level `entries:` key; schemas and actions have none. */
    fun isManifest(project: Project, file: VirtualFile): Boolean {
        return isYaml(file = file) &&
            RuleYamlKinds.kindOf(text = textOf(project = project, file = file)) == RuleYamlKind.MANIFEST
    }

    fun parse(project: Project, file: VirtualFile): ProjectManifest? {
        if (!isManifest(project = project, file = file)) {
            return null
        }
        return runCatching {
            ManifestLoader.loadFromString(content = textOf(project = project, file = file))
        }.getOrNull()
    }

    fun entryListing(manifestFile: VirtualFile, manifest: ProjectManifest, rule: VirtualFile): ManifestEntry? {
        return manifest.entries.firstOrNull { entry ->
            entry.rules.any { path -> resolve(manifestFile = manifestFile, relativePath = path) == rule }
        }
    }

    /** Resolves a manifest path as the engine does: relative to the manifest, `..` allowed, absolute kept. */
    fun resolve(manifestFile: VirtualFile, relativePath: String): VirtualFile? {
        if (Path.of(relativePath).isAbsolute) {
            return LocalFileSystem.getInstance().findFileByPath(relativePath)
        }
        return manifestFile.parent?.let { dir -> VfsUtilCore.findRelativeFile(relativePath, dir) }
    }

    /** The text as the editor holds it, so unsaved edits count. */
    fun textOf(project: Project, file: VirtualFile): String {
        return PsiManager.getInstance(project).findFile(file)?.text ?: VfsUtilCore.loadText(file)
    }
}
