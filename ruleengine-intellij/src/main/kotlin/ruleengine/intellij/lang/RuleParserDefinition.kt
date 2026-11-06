package ruleengine.intellij.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.ASTWrapperPsiElement
import ruleengine.intellij.lexer.RuleLexer
import ruleengine.intellij.lexer.RuleTokenTypes

/**
 * A flat PSI: the file holds the lexer's tokens and nothing else.
 *
 * The real parser lives in core and has no error recovery, so the IDE does not build a tree from it.
 * Completion, navigation and the diagram read the text or call core's parser themselves.
 */
class RuleParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer {
        return RuleLexer()
    }

    override fun createParser(project: Project?): PsiParser {
        return PsiParser { root, builder ->
            val marker = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            marker.done(root)
            builder.treeBuilt
        }
    }

    override fun getFileNodeType(): IFileElementType {
        return FILE
    }

    override fun getCommentTokens(): TokenSet {
        return RuleTokenTypes.COMMENTS
    }

    override fun getStringLiteralElements(): TokenSet {
        return RuleTokenTypes.STRINGS
    }

    override fun createElement(node: ASTNode): PsiElement {
        return ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return RuleFile(viewProvider)
    }

    private companion object {
        val FILE = IFileElementType(RuleLanguage)
    }
}
