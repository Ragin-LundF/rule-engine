package ruleengine.intellij.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar
import ruleengine.intellij.manifest.ManifestFiles

/** Ctrl/Cmd+click on a path in a manifest (`schema:`, `actions:`, `rules:`) opens that file. */
class ManifestGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val scalar = sourceElement?.parentOfType<YAMLScalar>(withSelf = true) ?: return null
        val yamlFile = scalar.containingFile as? YAMLFile ?: return null
        val manifestFile = yamlFile.originalFile.virtualFile ?: return null
        if (!ManifestFiles.isManifest(project = scalar.project, file = manifestFile)) {
            return null
        }
        val target = ManifestFiles.resolve(manifestFile = manifestFile, relativePath = scalar.textValue)
            ?.takeIf { file -> !file.isDirectory }
            ?.let { file -> PsiManager.getInstance(scalar.project).findFile(file) }
            ?: return null
        return arrayOf(target)
    }
}
