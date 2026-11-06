package ruleengine.intellij.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType
import ruleengine.intellij.lang.RuleFile
import ruleengine.intellij.lexer.RuleTokenTypes
import ruleengine.intellij.manifest.RuleSourcesLoader

/**
 * Finds the clauses that write a `$variable`: `set name = …` and `add … to name`, which spell the
 * name bare.
 *
 * Variables flow from earlier rules to later ones, so the search covers the open file and the other
 * rule files of its manifest entry, in manifest order. Those files are read only when a variable is
 * clicked; without a linked manifest only the open file is searched.
 */
object VariableWrites {
    private val WRITING_KEYWORDS = setOf("set", "to")

    fun of(reference: PsiElement): List<PsiElement> {
        val name = reference.text.removePrefix("$")
        val file = reference.containingFile as? RuleFile ?: return emptyList()
        // `$1`, `$2` … are extraction references to a regex group, not variables.
        if (name.isEmpty() || name.all { char -> char.isDigit() }) {
            return emptyList()
        }
        return filesToSearch(file = file).flatMap { ruleFile -> writesIn(file = ruleFile, name = name) }
    }

    private fun filesToSearch(file: RuleFile): List<RuleFile> {
        val psiManager = PsiManager.getInstance(file.project)
        val entryFiles = RuleSourcesLoader.sourcesFor(ruleFile = file)?.ruleFiles.orEmpty()
            .mapNotNull { ruleFile -> psiManager.findFile(ruleFile) as? RuleFile }
        val listed = entryFiles.any { entryFile -> entryFile.virtualFile == file.virtualFile }
        return if (listed) entryFiles else listOf(file) + entryFiles
    }

    private fun writesIn(file: RuleFile, name: String): List<PsiElement> {
        return file.children.filter { element ->
            element.node.elementType == RuleTokenTypes.IDENTIFIER &&
                element.text == name &&
                previousWord(element = element) in WRITING_KEYWORDS
        }
    }

    private fun previousWord(element: PsiElement): String? {
        var previous = element.prevSibling
        while (previous != null && previous.node.elementType == TokenType.WHITE_SPACE) {
            previous = previous.prevSibling
        }
        return previous?.takeIf { it.node.elementType == RuleTokenTypes.KEYWORD }?.text
    }
}
