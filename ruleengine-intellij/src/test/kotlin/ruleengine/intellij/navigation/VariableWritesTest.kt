package ruleengine.intellij.navigation

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VariableWritesTest : BasePlatformTestCase() {
    fun testVariableJumpsToItsSetClauseInTheSameFile() {
        myFixture.configureByText("alone.rule", WRITER + "rule \"read\" { when \$turnover > 1 then stop }\n")

        val targets = targetsAt(word = "\$turnover")

        assertEquals(listOf("set turnover"), targets.map { target -> "${keywordBefore(target)} ${target.text}" })
    }

    fun testVariableFindsWritesInEarlierRuleFilesOfTheEntry() {
        val manifest = "entries:\n  - id: e\n    rules:\n      - a.rule\n      - b.rule\n"
        myFixture.addFileToProject("vars/manifest.yaml", manifest)
        myFixture.addFileToProject("vars/a.rule", "rule \"a\" { when x > 1 then add \"one\" to checks }\n")
        val reader = myFixture.addFileToProject(
            "vars/b.rule",
            "rule \"b\" { when x > 2 then add \"two\" to checks }\n" +
                "rule \"c\" { when \$checks contains \"one\" then stop }\n",
        )
        myFixture.configureFromExistingVirtualFile(reader.virtualFile)

        val targets = targetsAt(word = "\$checks")

        assertEquals(listOf("a.rule", "b.rule"), targets.map { target -> target.containingFile.name })
        assertEquals(listOf("to", "to"), targets.map { target -> keywordBefore(target) })
    }

    fun testExtractionReferenceAndUnwrittenVariableHaveNoTarget() {
        myFixture.configureByText(
            "alone.rule",
            "rule \"r\" { when matches(x, \"(a)\") then label \$1 }\nrule \"s\" { when \$none > 1 then stop }\n",
        )

        assertTrue(targetsAt(word = "\$1").isEmpty())
        assertTrue(targetsAt(word = "\$none").isEmpty())
    }

    private fun targetsAt(word: String): List<PsiElement> {
        val offset = myFixture.editor.document.text.indexOf(word) + 1
        val element = myFixture.file.findElementAt(offset)
        val targets = RuleGotoDeclarationHandler().getGotoDeclarationTargets(element, offset, myFixture.editor)
        return targets?.toList().orEmpty()
    }

    /** The keyword two tokens before [target], across the whitespace between them. */
    private fun keywordBefore(target: PsiElement): String {
        return target.prevSibling.prevSibling.text
    }

    private companion object {
        const val WRITER = "rule \"write\" { when x > 1 then set turnover = sum(t.amount) }\n"
    }
}
