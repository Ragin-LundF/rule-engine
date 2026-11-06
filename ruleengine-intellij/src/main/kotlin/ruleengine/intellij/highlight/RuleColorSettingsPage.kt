package ruleengine.intellij.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import ruleengine.intellij.lang.RuleFileType
import javax.swing.Icon

class RuleColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String {
        return "Rule DSL"
    }

    override fun getIcon(): Icon {
        return RuleFileType.icon
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return RuleSyntaxHighlighter()
    }

    override fun getDemoText(): String {
        return DEMO
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? {
        return null
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return DESCRIPTORS
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return ColorDescriptor.EMPTY_ARRAY
    }

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", RuleColors.KEYWORD),
            AttributesDescriptor("Logic (and, or, not)", RuleColors.LOGIC),
            AttributesDescriptor("Boolean", RuleColors.BOOLEAN),
            AttributesDescriptor("String", RuleColors.STRING),
            AttributesDescriptor("Number", RuleColors.NUMBER),
            AttributesDescriptor("Comment", RuleColors.COMMENT),
            AttributesDescriptor("Operator sign", RuleColors.OPERATOR),
            AttributesDescriptor("Named operator", RuleColors.NAMED_OPERATOR),
            AttributesDescriptor("Function", RuleColors.FUNCTION),
            AttributesDescriptor("Variable", RuleColors.VARIABLE),
            AttributesDescriptor("Identifier", RuleColors.IDENTIFIER),
            AttributesDescriptor("Schema field", RuleColors.FIELD),
            AttributesDescriptor("Action", RuleColors.ACTION),
            AttributesDescriptor("Braces", RuleColors.BRACES),
            AttributesDescriptor("Parentheses", RuleColors.PARENTHESES),
            AttributesDescriptor("Brackets", RuleColors.BRACKETS),
            AttributesDescriptor("Punctuation", RuleColors.PUNCTUATION),
            AttributesDescriptor("Bad character", RuleColors.BAD_CHARACTER),
        )

        val DEMO = """
            # Customers with a high score get the VIP label.
            rule "vip-customer" {
              description "Score above 900 and an active account."
              when
                customer.score >= 900
                and customer.status in ["ACTIVE", "TRIAL"]
                and not count(orders[amount > 10.5]) == 0
              then
                label "vip"
                set bonus = 1
            }
        """.trimIndent()
    }
}
