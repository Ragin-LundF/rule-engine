package ruleengine.intellij.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ruleengine.intellij.manifest.RuleProjectFiles

class RuleCompletionContributorTest : BasePlatformTestCase() {
    fun testWhenOffersSchemaFieldsAndFunctions() {
        configureRule(body = "<caret>")

        val offered = myFixture.completeBasic().map { element -> element.lookupString }

        assertTrue(offered.containsAll(listOf("score", "points", "customer.tier", "and", "count(transactions)")))
    }

    fun testNestedPathCompletesAsAWhole() {
        configureRule(body = "customer.ti<caret>")

        myFixture.completeBasic()

        myFixture.checkResult("rule \"vip\" {\n  when\n    customer.tier\n}\n")
    }

    fun testOperatorsFollowAFinishedField() {
        configureRule(body = "score <caret>")

        val offered = myFixture.completeBasic().map { element -> element.lookupString }

        assertTrue(offered.contains(">= 0"))
    }

    fun testValueAfterANamedOperatorIsOfferedAlone() {
        configureRule(body = "score gte <caret>")

        val offered = myFixture.completeBasic().map { element -> element.lookupString }

        assertEquals(listOf("0"), offered)
    }

    fun testThenOffersActionsFromTheActionSchema() {
        configureRule(body = "score > 1\n  then\n    <caret>")

        val offered = myFixture.completeBasic().map { element -> element.lookupString }

        assertTrue(offered.contains("label \"value\""))
    }

    fun testCompletionWorksWithoutManifest() {
        myFixture.configureByText("alone.rule", "rule \"a\" {\n  when\n    <caret>\n}\n")

        val offered = myFixture.completeBasic().map { element -> element.lookupString }

        assertTrue(offered.containsAll(listOf("and", "not")))
    }

    /** Opens the fixture rule `vip`, whose `when` block holds [body], in the linked test project. */
    private fun configureRule(body: String) {
        val rule = RuleProjectFiles.create(fixture = myFixture, ruleText = "rule \"vip\" {\n  when\n    $body\n}\n")
        myFixture.configureFromExistingVirtualFile(rule)
    }
}
