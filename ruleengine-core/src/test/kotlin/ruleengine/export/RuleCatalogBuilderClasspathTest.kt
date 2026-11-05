package ruleengine.export

import ruleengine.builder.ClasspathProjectJars.SCHEMA_YAML
import ruleengine.builder.ClasspathProjectJars.manifest
import ruleengine.builder.ClasspathProjectJars.nestedProject
import ruleengine.builder.ClasspathProjectJars.ruleFile
import ruleengine.builder.ClasspathProjectJars.withJarClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleCatalogBuilderClasspathTest {
    @Test
    fun `builds a catalog from a manifest inside a jar`() {
        val entries = nestedProject() + mapOf(
            "rules/manifest.yaml" to manifest(rules = listOf("rules/a.rule", "rules/b.rule")),
            "rules/rules/b.rule" to ruleFile(id = "b"),
        )

        withJarClassLoader(entries = entries) { loader ->
            val catalog = RuleCatalogBuilder.fromManifest(
                manifestLocation = "classpath:rules/manifest.yaml",
                classLoader = loader,
            ).single()

            assertEquals(expected = "classpath-test", actual = catalog.projectName)
            assertEquals(expected = "e", actual = catalog.entryId)
            assertEquals(expected = "schema.yaml", actual = catalog.schemaPath)
            // File grouping and manifest order survive the classpath route.
            assertEquals(
                expected = listOf("rules/a.rule", "rules/b.rule"),
                actual = catalog.files.map { file -> file.relativePath },
            )
            assertEquals(expected = listOf("a", "b"), actual = catalog.rules.map { rule -> rule.id })
        }
    }

    @Test
    fun `a resource path above the manifest folder reaches a shared resource`() {
        val entries = nestedProject() + mapOf(
            "rules/manifest.yaml" to manifest(schema = "../shared/schema.yaml"),
            "shared/schema.yaml" to SCHEMA_YAML,
        )

        withJarClassLoader(entries = entries) { loader ->
            val catalog = RuleCatalogBuilder.fromManifest(
                manifestLocation = "classpath:rules/manifest.yaml",
                classLoader = loader,
            ).single()

            assertEquals(expected = "../shared/schema.yaml", actual = catalog.schemaPath)
            assertEquals(expected = listOf("a"), actual = catalog.rules.map { rule -> rule.id })
        }
    }
}
