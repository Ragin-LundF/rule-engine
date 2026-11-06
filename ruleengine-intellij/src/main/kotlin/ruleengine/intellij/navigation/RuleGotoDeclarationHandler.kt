package ruleengine.intellij.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import ruleengine.intellij.lang.RuleFile
import ruleengine.intellij.lexer.RuleTokenTypes
import ruleengine.intellij.manifest.RuleSourcesLoader
import ruleengine.intellij.manifest.model.RuleSources
import ruleengine.intellij.navigation.model.SchemaKeyPath
import ui.dsl.analyzeDslContext

/**
 * Ctrl+click (Cmd+click on macOS) in a rule: a field path or alias jumps into the schema YAML, an
 * action name in a `then`/`else`/`not_exists` block into the action YAML, and a `$variable` to the
 * clauses that write it.
 */
class RuleGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val file = element.containingFile as? RuleFile ?: return null
        return when (element.node.elementType) {
            RuleTokenTypes.VARIABLE -> VariableWrites.of(reference = element).takeIf { it.isNotEmpty() }?.toTypedArray()
            RuleTokenTypes.IDENTIFIER -> schemaTarget(element = element, file = file)?.let { arrayOf(it) }
            else -> null
        }
    }

    private fun schemaTarget(element: PsiElement, file: RuleFile): PsiElement? {
        if (RulePathReader.isLocalName(element = element)) {
            return null
        }
        val sources = RuleSourcesLoader.sourcesFor(ruleFile = file) ?: return null
        return actionTarget(element = element, sources = sources)
            ?: fieldTarget(element = element, sources = sources)
    }

    private fun actionTarget(element: PsiElement, sources: RuleSources): PsiElement? {
        val name = element.text
        val context = analyzeDslContext(text = element.containingFile.text, cursorPos = element.textRange.startOffset)
        val inBranch = context.section.isBranch()
        if (!inBranch || name !in sources.actionNames) {
            return null
        }
        val keyValue = keyValueIn(project = element.project, file = sources.actionsFile, keys = listOf("actions", name))
        return keyValue?.let { it.key ?: it }
    }

    private fun fieldTarget(element: PsiElement, sources: RuleSources): PsiElement? {
        val schema = sources.schema ?: return null
        val path = RulePathReader.pathUpTo(segment = element)
        val keyPath = SchemaKeys.keysFor(path = path, schema = schema)
            ?: RulePathReader.enclosingCollection(element = element)?.let { collection ->
                SchemaKeys.keysFor(path = "$collection.$path", schema = schema)
            }
            ?: return null
        val keyValue = keyValueIn(project = element.project, file = sources.schemaFile, keys = keyPath.keys)
            ?: return null
        return aliasValueOf(keyValue = keyValue, keyPath = keyPath) ?: keyValue.key ?: keyValue
    }

    /** A field written by its alias lands on the `alias:` value rather than on the field's key. */
    private fun aliasValueOf(keyValue: YAMLKeyValue, keyPath: SchemaKeyPath): PsiElement? {
        if (!keyPath.viaAlias) {
            return null
        }
        return (keyValue.value as? YAMLMapping)?.getKeyValueByKey("alias")?.value
    }

    private fun keyValueIn(project: Project, file: VirtualFile?, keys: List<String>): YAMLKeyValue? {
        val yaml = file?.let { PsiManager.getInstance(project).findFile(it) } as? YAMLFile ?: return null
        return YAMLUtil.getQualifiedKeyInFile(yaml, keys)
    }
}
