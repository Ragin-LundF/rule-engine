package ruleengine.intellij.highlight

import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ruleengine.intellij.lang.RuleFile
import ruleengine.intellij.lexer.RuleTokenTypes
import ruleengine.intellij.manifest.RuleProjectFiles

/** Runs inside a headless IDE: proves the plugin loads there and core's classes work on its runtime. */
class RuleHighlightingTest : BasePlatformTestCase() {
    fun testRuleFileIsHighlighted() {
        val file = myFixture.configureByText("sample.rule", "rule \"r\" { when matches(x, \"a\") then stop }")
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.language, project, file.virtualFile)

        assertInstanceOf(file, RuleFile::class.java)
        assertEquals(RuleColors.KEYWORD, highlighter.getTokenHighlights(RuleTokenTypes.KEYWORD).single())
    }

    fun testSchemaFieldsAndActionsAreColoured() {
        val ruleText = "rule \"r\" { when points > 1 then label \"x\" }"
        val rule = RuleProjectFiles.create(fixture = myFixture, ruleText = ruleText)
        myFixture.configureFromExistingVirtualFile(rule)

        val colours = myFixture.doHighlighting().associate { info -> info.text to info.forcedTextAttributesKey }

        assertEquals(RuleColors.FIELD, colours["points"])
        assertEquals(RuleColors.ACTION, colours["label"])
    }

    fun testCommentLineAction() {
        myFixture.configureByText("sample.rule", "<caret>rule \"r\" {}")
        myFixture.performEditorAction("CommentByLineComment")

        myFixture.checkResult("# rule \"r\" {}")
    }
}
