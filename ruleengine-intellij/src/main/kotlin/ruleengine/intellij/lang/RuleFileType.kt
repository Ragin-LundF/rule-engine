package ruleengine.intellij.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object RuleFileType : LanguageFileType(RuleLanguage) {
    override fun getName(): String {
        return "Rule DSL"
    }

    override fun getDescription(): String {
        return "Rule engine rule file"
    }

    override fun getDefaultExtension(): String {
        return "rule"
    }

    override fun getIcon(): Icon {
        return AllIcons.FileTypes.Custom
    }
}
