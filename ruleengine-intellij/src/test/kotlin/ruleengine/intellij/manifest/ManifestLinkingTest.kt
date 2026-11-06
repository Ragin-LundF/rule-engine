package ruleengine.intellij.manifest

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ruleengine.intellij.manifest.model.ManifestLinkState
import ruleengine.intellij.manifest.model.RuleSources
import ruleengine.intellij.manifest.toolwindow.ManifestTreeModel

class ManifestLinkingTest : BasePlatformTestCase() {
    override fun tearDown() {
        // The light test project outlives a test, and with it the stored links.
        ManifestLinks.getInstance(project).loadState(ManifestLinkState())
        super.tearDown()
    }

    fun testRuleListedByOneManifestIsLinkedAndItsSchemasLoad() {
        val rule = RuleProjectFiles.create(fixture = myFixture)

        val sources = RuleSourcesLoader.sourcesFor(ruleFile = myFixture.psiManager.findFile(rule)!!)!!

        assertEquals("customers", sources.entryId)
        assertEquals("manifest.yaml", ManifestLinks.getInstance(project).manifestFor(rule = rule)!!.name)
        assertTrue(sources.problems.isEmpty())
        assertTrue(sources.fieldNames.containsAll(listOf("score", "points", "customer.tier")))
        assertEquals(setOf("label"), sources.actionNames)
    }

    fun testRuleListedByNoManifestHasNoSources() {
        val rule = myFixture.addFileToProject("loose/alone.rule", "rule \"a\" {}").virtualFile

        assertNull(RuleSourcesLoader.sourcesFor(ruleFile = myFixture.psiManager.findFile(rule)!!))
    }

    fun testRuleListedByTwoManifestsWaitsForAChoice() {
        val rule = RuleProjectFiles.create(fixture = myFixture)
        val otherManifest = "entries:\n  - id: other\n    rules:\n      - vip.rule\n"
        val second = myFixture.addFileToProject("project/rules/other-manifest.yaml", otherManifest).virtualFile

        assertEquals(2, ManifestDetector.manifestsListing(project = project, rule = rule).size)
        assertNull(ManifestLinks.getInstance(project).manifestFor(rule = rule))

        ManifestLinks.getInstance(project).link(rule = rule, manifest = second)

        assertEquals("other", sourcesOf(rule = rule)!!.entryId)
    }

    fun testSchemaThatDoesNotLoadIsReported() {
        val brokenSchema = "schema: broken\nfields:\n  score:\n    type: nonsense\n"
        val rule = RuleProjectFiles.create(fixture = myFixture, schemaText = brokenSchema)

        val sources = sourcesOf(rule = rule)!!

        assertNull(sources.schema)
        assertTrue(sources.problems.single().startsWith("schema.yaml could not be loaded"))
    }

    fun testManifestTreeListsEntryFiles() {
        RuleProjectFiles.create(fixture = myFixture)
        val manifest = myFixture.findFileInTempDir("project/manifest.yaml")

        val root = ManifestTreeModel.build(project = project, manifestFile = manifest)!!

        val entry = root.getChildAt(0)
        assertEquals("entry: customers", entry.toString())
        val children = (0 until entry.childCount).map { index -> entry.getChildAt(index).toString() }
        assertEquals(listOf("schema: schemas/schema.yaml", "actions: schemas/actions.yaml", "rules (1)"), children)
    }

    private fun sourcesOf(rule: VirtualFile): RuleSources? {
        return RuleSourcesLoader.sourcesFor(ruleFile = myFixture.psiManager.findFile(rule)!!)
    }
}
