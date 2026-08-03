package ruleengine.builder

import ruleengine.builder.ClasspathProjectJars.ACTIONS_YAML
import ruleengine.builder.ClasspathProjectJars.SCHEMA_YAML
import ruleengine.builder.ClasspathProjectJars.manifest
import ruleengine.builder.ClasspathProjectJars.nestedProject
import ruleengine.builder.ClasspathProjectJars.ruleFile
import ruleengine.builder.ClasspathProjectJars.withJarClassLoader
import ruleengine.core.errors.RuleEngineBuildException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuleEngineBuilderClasspathTest {
    @Test
    fun `builds an entry from a manifest inside a jar`() {
        withJarClassLoader(entries = nestedProject()) { loader ->
            // Guards the test itself: if this is not a jar URL, the assertions below prove nothing.
            assertEquals(
                expected = "jar",
                actual = loader.getResource("rules/manifest.yaml")!!.protocol,
                message = "the fixture must serve the manifest out of a jar"
            )

            val loaded = RuleEngineBuilder.fromManifestEntry(
                manifestLocation = "classpath:rules/manifest.yaml",
                entryId = "e",
                classLoader = loader,
            )

            assertEquals(expected = "e", actual = loaded.entryId)
            assertNotNull(actual = loaded.actions, message = "the manifest declares an action schema")
            assertEquals(expected = emptyList(), actual = loaded.warnings)
            assertEquals(
                expected = listOf("a"),
                actual = loaded.evaluate(input = MATCHING_INPUT).matches.map { it.ruleId }
            )
            assertTrue(
                actual = loaded.evaluate(input = mapOf("p" to "other")).matches.isEmpty(),
                message = "a non matching input must not match"
            )
        }
    }

    @Test
    fun `a manifest at the jar root resolves sibling resources`() {
        val entries = mapOf(
            "manifest.yaml" to manifest(rules = listOf("a.rule")),
            "schema.yaml" to SCHEMA_YAML,
            "actions.yaml" to ACTIONS_YAML,
            "a.rule" to ruleFile(id = "a"),
        )

        withJarClassLoader(entries = entries) { loader ->
            val loaded = RuleEngineBuilder.fromManifestEntry(
                manifestLocation = "classpath:manifest.yaml",
                entryId = "e",
                classLoader = loader,
            )

            assertEquals(
                expected = listOf("a"),
                actual = loaded.evaluate(input = MATCHING_INPUT).matches.map { it.ruleId }
            )
        }
    }

    @Test
    fun `a leading slash in the manifest resource is accepted`() {
        withJarClassLoader(entries = nestedProject()) { loader ->
            val loaded = RuleEngineBuilder.fromManifestEntry(
                manifestLocation = "classpath:/rules/manifest.yaml",
                entryId = "e",
                classLoader = loader,
            )

            assertEquals(expected = "e", actual = loaded.entryId)
        }
    }

    @Test
    fun `every entry of a classpath manifest is built by default`() {
        val multiEntryManifest = """
            name: classpath-multi
            entries:
              - id: first
                schema: schema.yaml
                rules:
                  - rules/a.rule
              - id: second
                schema: schema.yaml
                rules:
                  - rules/b.rule
        """.trimIndent()
        val entries = nestedProject() + mapOf(
            "rules/manifest.yaml" to multiEntryManifest,
            "rules/rules/b.rule" to ruleFile(id = "b"),
        )

        withJarClassLoader(entries = entries) { loader ->
            val engines = RuleEngineBuilder.fromManifest(
                manifestLocation = "classpath:rules/manifest.yaml",
                classLoader = loader,
            )

            assertEquals(expected = listOf("first", "second"), actual = engines.keys.toList())
            assertEquals(
                expected = listOf("b"),
                actual = engines.getValue("second").evaluate(input = MATCHING_INPUT).matches.map { it.ruleId }
            )
        }
    }

    @Test
    fun `a resource path escaping the manifest base is rejected`() {
        // The escape target exists and is valid, so a rejection cannot be mistaken for "not found".
        val entries = nestedProject() + mapOf(
            "rules/manifest.yaml" to manifest(schema = "../secret-schema.yaml"),
            "secret-schema.yaml" to SCHEMA_YAML,
        )

        withJarClassLoader(entries = entries) { loader ->
            val failure = assertFailsWith<RuleEngineBuildException> {
                RuleEngineBuilder.fromManifest(manifestLocation = "classpath:rules/manifest.yaml", classLoader = loader)
            }

            assertTrue(
                actual = failure.message!!.contains(other = "escapes base directory"),
                message = failure.message!!
            )
            assertEquals(expected = "e", actual = failure.entryId)
        }
    }

    @Test
    fun `a missing classpath resource fails naming the relative and the resolved resource`() {
        val entries = nestedProject() + mapOf("rules/manifest.yaml" to manifest(schema = "gone.yaml"))

        withJarClassLoader(entries = entries) { loader ->
            val failure = assertFailsWith<RuleEngineBuildException> {
                RuleEngineBuilder.fromManifest(manifestLocation = "classpath:rules/manifest.yaml", classLoader = loader)
            }

            assertTrue(
                actual = failure.message!!.contains(other = "schema file 'gone.yaml' not found"),
                message = failure.message!!
            )
            assertTrue(
                actual = failure.message!!.contains(other = "rules/gone.yaml"),
                message = failure.message!!
            )
            assertEquals(expected = "e", actual = failure.entryId)
        }
    }

    @Test
    fun `a missing manifest resource fails as unreadable`() {
        withJarClassLoader(entries = nestedProject()) { loader ->
            val failure = assertFailsWith<RuleEngineBuildException> {
                RuleEngineBuilder.fromManifest(manifestLocation = "classpath:rules/nope.yaml", classLoader = loader)
            }

            assertTrue(
                actual = failure.message!!.contains(other = "manifest is not readable"),
                message = failure.message!!
            )
        }
    }

    /**
     * The Spring Boot stand-in: nothing on the load path may turn a resource into a `URL`, because
     * Boot's nested-jar URLs have no `FileSystemProvider`.
     */
    @Test
    fun `rules load through a loader that only serves streams`() {
        val loader = ResourceStreamOnlyClassLoader(resources = nestedProject())

        val loaded = RuleEngineBuilder.fromManifestEntry(
            manifestLocation = "classpath:rules/manifest.yaml",
            entryId = "e",
            classLoader = loader,
        )

        assertEquals(
            expected = listOf("a"),
            actual = loaded.evaluate(input = MATCHING_INPUT).matches.map { it.ruleId }
        )
    }

    private companion object {
        val MATCHING_INPUT: Map<String, Any?> = mapOf("p" to "x")
    }
}
