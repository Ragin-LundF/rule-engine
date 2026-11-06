package ruleengine.intellij.navigation

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLKeyValue
import ruleengine.intellij.manifest.RuleProjectFiles

class RuleGotoDeclarationHandlerTest : BasePlatformTestCase() {
    private val rule = """
        rule "vip" {
          when
            customer.tier == "gold" and points > 1
            and count(orders[amount > 1]) > 0
            and count(joinByKey("k", credit: orders.amount, debit: orders.amount)) > 0
          then
            label "vip"
        }
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        myFixture.configureFromExistingVirtualFile(RuleProjectFiles.create(fixture = myFixture, ruleText = rule))
    }

    fun testEachPathSegmentJumpsToItsSchemaKey() {
        assertEquals("tier" to "schema.yaml", targetOf(word = "tier"))
        assertEquals("customer" to "schema.yaml", targetOf(word = "customer"))
    }

    fun testAliasJumpsToTheAliasValue() {
        val target = gotoTarget(word = "points")!!

        assertEquals("points", target.text)
        assertEquals("score", (target.parent.parent.parent as YAMLKeyValue).keyText)
    }

    fun testFilterMemberResolvesInsideTheCollection() {
        val target = gotoTarget(word = "amount")!!

        assertEquals("amount", target.text)
        assertEquals("fields", (target.parent.parent.parent as YAMLKeyValue).keyText)
    }

    fun testActionNameJumpsToTheActionSchema() {
        assertEquals("label" to "actions.yaml", targetOf(word = "label"))
    }

    fun testJoinByKeyLocalNameHasNoTarget() {
        assertNull(gotoTarget(word = "credit"))
    }

    fun testManifestPathOpensTheFile() {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("project/manifest.yaml"))
        val offset = myFixture.editor.document.text.indexOf("rules/vip.rule") + 2

        val element = myFixture.file.findElementAt(offset)

        val targets = ManifestGotoDeclarationHandler().getGotoDeclarationTargets(element, offset, myFixture.editor)

        assertEquals("vip.rule", targets!!.single().containingFile.name)
    }

    private fun targetOf(word: String): Pair<String, String>? {
        return gotoTarget(word = word)?.let { target -> target.text to target.containingFile.name }
    }

    private fun gotoTarget(word: String): PsiElement? {
        val offset = Regex(pattern = "\\b$word\\b").find(input = rule)!!.range.first + 1
        val element = myFixture.file.findElementAt(offset)
        val targets = RuleGotoDeclarationHandler().getGotoDeclarationTargets(element, offset, myFixture.editor)
        return targets?.single()
    }
}
