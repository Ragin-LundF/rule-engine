package ruleengine.intellij.manifest

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import ruleengine.intellij.lang.RuleFileType
import java.util.function.Function
import javax.swing.JComponent

/** What IntelliJ expects from a provider: the banner for an editor, or null for none. */
private typealias Banner = Function<in FileEditor, out JComponent?>

/**
 * The banner above a rule file whose manifest is unknown, ambiguous, or names schemas that do not load.
 *
 * A rule listed by exactly one manifest is linked without asking, so then there is no banner at all.
 */
class ManifestNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Banner? {
        if (file.fileType != RuleFileType) {
            return null
        }
        val ruleFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val sources = RuleSourcesLoader.sourcesFor(ruleFile = ruleFile)
        if (sources != null) {
            return if (sources.problems.isEmpty()) {
                null
            } else {
                problemsBanner(project = project, manifest = sources.manifest, problems = sources.problems)
            }
        }
        val candidates = ManifestDetector.manifestsListing(project = project, rule = file)
        return Banner { editor ->
            missingBanner(project = project, editor = editor, rule = file, candidates = candidates)
        }
    }

    private fun problemsBanner(project: Project, manifest: VirtualFile, problems: List<String>): Banner {
        return Banner { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning).apply {
                text = problems.joinToString(separator = "; ")
                createActionLabel("Open manifest") { FileEditorManager.getInstance(project).openFile(manifest, true) }
            }
        }
    }

    private fun missingBanner(
        project: Project,
        editor: FileEditor,
        rule: VirtualFile,
        candidates: List<VirtualFile>,
    ): JComponent {
        return EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
            text = if (candidates.isEmpty()) {
                "No manifest in this folder or above lists this rule. Completion and navigation need its schema."
            } else {
                "Several manifests list this rule. Choose the one to use:"
            }
            candidates.forEach { manifest ->
                createActionLabel(displayPath(project = project, file = manifest)) {
                    ManifestLinks.getInstance(project).link(rule = rule, manifest = manifest)
                }
            }
            createActionLabel("Select manifest…") { chooseManifest(project = project, rule = rule) }
        }
    }

    private fun chooseManifest(project: Project, rule: VirtualFile) {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withFileFilter { file -> ManifestFiles.isYaml(file = file) }
            .withTitle("Select the Manifest of ${rule.name}")
        val chosen = FileChooser.chooseFile(descriptor, project, rule.parent) ?: return
        if (ManifestDetector.lists(project = project, manifestFile = chosen, rule = rule)) {
            ManifestLinks.getInstance(project).link(rule = rule, manifest = chosen)
        } else {
            val message = "${chosen.name} does not list ${rule.name} in any entry."
            Messages.showErrorDialog(project, message, "Rule Manifest")
        }
    }

    private fun displayPath(project: Project, file: VirtualFile): String {
        return project.guessProjectDir()?.let { base -> VfsUtilCore.getRelativePath(file, base) } ?: file.path
    }
}
