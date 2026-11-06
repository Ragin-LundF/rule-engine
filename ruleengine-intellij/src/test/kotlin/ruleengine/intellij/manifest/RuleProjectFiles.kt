package ruleengine.intellij.manifest

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/** A small rule project in the test fixture: one manifest, a schema, actions and a rule. */
object RuleProjectFiles {
    const val RULE_PATH = "project/rules/vip.rule"

    fun create(
        fixture: CodeInsightTestFixture,
        ruleText: String = "rule \"vip\" {\n  when\n    \n}\n",
        schemaText: String = SCHEMA,
    ): VirtualFile {
        fixture.addFileToProject("project/manifest.yaml", MANIFEST)
        fixture.addFileToProject("project/schemas/schema.yaml", schemaText)
        fixture.addFileToProject("project/schemas/actions.yaml", ACTIONS)
        return fixture.addFileToProject(RULE_PATH, ruleText).virtualFile
    }

    val MANIFEST = """
        name: shop
        entries:
          - id: customers
            schema: schemas/schema.yaml
            actions: schemas/actions.yaml
            rules:
              - rules/vip.rule
    """.trimIndent()

    val SCHEMA = """
        schema: customers-v1
        fields:
          score:
            type: integer
            alias: points
          customer:
            type: object
            fields:
              tier:
                type: text
          orders:
            type: collection
            fields:
              amount:
                type: decimal
    """.trimIndent()

    val ACTIONS = """
        actions:
          label:
            argTypes: [string]
    """.trimIndent()
}
