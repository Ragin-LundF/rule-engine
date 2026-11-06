package ruleengine.intellij.lang

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import ruleengine.intellij.lexer.RuleTokenTypes

class RuleBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> {
        return PAIRS
    }

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
    }

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }

    private companion object {
        val PAIRS = arrayOf(
            BracePair(RuleTokenTypes.LBRACE, RuleTokenTypes.RBRACE, true),
            BracePair(RuleTokenTypes.LPAREN, RuleTokenTypes.RPAREN, false),
            BracePair(RuleTokenTypes.LBRACKET, RuleTokenTypes.RBRACKET, false),
        )
    }
}
