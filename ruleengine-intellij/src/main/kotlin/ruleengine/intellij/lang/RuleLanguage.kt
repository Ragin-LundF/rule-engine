package ruleengine.intellij.lang

import com.intellij.lang.Language

object RuleLanguage : Language("RuleDsl") {
    private fun readResolve(): Any {
        return RuleLanguage
    }

    override fun getDisplayName(): String {
        return "Rule DSL"
    }
}
