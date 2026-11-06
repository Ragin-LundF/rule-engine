package ruleengine.intellij.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

/** The colour keys of rule files, each falling back to the matching default of the colour scheme. */
object RuleColors {
    val KEYWORD: TextAttributesKey = createTextAttributesKey("RULE_KEYWORD", Default.KEYWORD)
    val LOGIC: TextAttributesKey = createTextAttributesKey("RULE_LOGIC", Default.KEYWORD)
    val BOOLEAN: TextAttributesKey = createTextAttributesKey("RULE_BOOLEAN", Default.NUMBER)
    val STRING: TextAttributesKey = createTextAttributesKey("RULE_STRING", Default.STRING)
    val NUMBER: TextAttributesKey = createTextAttributesKey("RULE_NUMBER", Default.NUMBER)
    val COMMENT: TextAttributesKey = createTextAttributesKey("RULE_COMMENT", Default.LINE_COMMENT)
    val OPERATOR: TextAttributesKey = createTextAttributesKey("RULE_OPERATOR", Default.OPERATION_SIGN)
    val NAMED_OPERATOR: TextAttributesKey = createTextAttributesKey("RULE_NAMED_OPERATOR", Default.OPERATION_SIGN)
    val FUNCTION: TextAttributesKey = createTextAttributesKey("RULE_FUNCTION", Default.FUNCTION_CALL)
    val VARIABLE: TextAttributesKey = createTextAttributesKey("RULE_VARIABLE", Default.LOCAL_VARIABLE)
    val IDENTIFIER: TextAttributesKey = createTextAttributesKey("RULE_IDENTIFIER", Default.IDENTIFIER)
    val FIELD: TextAttributesKey = createTextAttributesKey("RULE_FIELD", Default.INSTANCE_FIELD)
    val ACTION: TextAttributesKey = createTextAttributesKey("RULE_ACTION", Default.FUNCTION_DECLARATION)
    val BRACES: TextAttributesKey = createTextAttributesKey("RULE_BRACES", Default.BRACES)
    val PARENTHESES: TextAttributesKey = createTextAttributesKey("RULE_PARENTHESES", Default.PARENTHESES)
    val BRACKETS: TextAttributesKey = createTextAttributesKey("RULE_BRACKETS", Default.BRACKETS)
    val PUNCTUATION: TextAttributesKey = createTextAttributesKey("RULE_PUNCTUATION", Default.COMMA)
    val BAD_CHARACTER: TextAttributesKey =
        createTextAttributesKey("RULE_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
}
