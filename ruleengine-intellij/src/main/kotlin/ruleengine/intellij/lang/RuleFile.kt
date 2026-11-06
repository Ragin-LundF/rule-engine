package ruleengine.intellij.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class RuleFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RuleLanguage) {
    override fun getFileType(): FileType {
        return RuleFileType
    }
}
