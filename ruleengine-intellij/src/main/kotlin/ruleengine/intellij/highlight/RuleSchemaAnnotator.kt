package ruleengine.intellij.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import ruleengine.intellij.lexer.RuleTokenTypes
import ruleengine.intellij.manifest.RuleSourcesLoader

/** Colours words the linked schema knows as fields or actions, like the UI editor does. */
class RuleSchemaAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != RuleTokenTypes.IDENTIFIER) {
            return
        }
        val sources = RuleSourcesLoader.sourcesFor(ruleFile = element.containingFile) ?: return
        val color = when (element.text) {
            in sources.fieldNames -> RuleColors.FIELD
            in sources.actionNames -> RuleColors.ACTION
            else -> return
        }
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(color).create()
    }
}
