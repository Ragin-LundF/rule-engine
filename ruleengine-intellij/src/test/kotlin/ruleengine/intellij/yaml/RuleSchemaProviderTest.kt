package ruleengine.intellij.yaml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.schema.YamlJsonSchemaHighlightingInspection
import java.io.File

class RuleSchemaProviderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(YamlJsonSchemaHighlightingInspection::class.java)
    }

    fun testUnknownFieldTypeAndNormalizerAreFlagged() {
        myFixture.configureByText(
            "schema.yaml",
            "schema: s\nfields:\n  score:\n    type: <warning>integr</warning>\n" +
                "    normalizers: [<warning>trimm</warning>]\n",
        )

        myFixture.checkHighlighting()
    }

    fun testUnknownManifestKeyIsFlagged() {
        myFixture.configureByText("manifest.yaml", "entries:\n  - id: a\n    <warning>rule</warning>: [x.rule]\n")

        myFixture.checkHighlighting()
    }

    fun testFieldTypeIsCompleted() {
        myFixture.configureByText("schema.yaml", "fields:\n  score:\n    type: dec<caret>\n")

        val offered = myFixture.completeBasic().map { element -> element.lookupString }

        assertEquals(listOf("decimal", "bigdecimal"), offered)
    }

    fun testActionArgTypesAreValid() {
        myFixture.configureByText("actions.yaml", "actions:\n  label:\n    argTypes: [string, variable_list]\n")

        myFixture.checkHighlighting()
    }

    /** The engine's own example files must not get a single false warning. */
    fun testCoreExampleFilesAreValid() {
        val resources = File("../ruleengine-core/src/test/resources")
        val files = listOf(
            "full-schema.yaml", "full-actions.yaml", "full-manifest.yaml", "sample-schema.yaml", "manifest.yaml",
        )
        files.forEach { name ->
            myFixture.configureByText(name, resources.resolve(name).readText())

            myFixture.checkHighlighting()
        }
    }

    fun testOtherYamlFilesGetNoRuleSchema() {
        myFixture.configureByText("other.yaml", "foo: bar\n")

        myFixture.checkHighlighting()
    }
}
