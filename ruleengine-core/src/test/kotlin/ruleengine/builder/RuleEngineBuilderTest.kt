package ruleengine.builder

import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.core.errors.Severity
import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleEngineBuilderTest {
    @Test
    fun `loads every manifest entry by default`() {
        val engines = RuleEngineBuilder.fromManifest(manifestPath = FULL_MANIFEST)

        assertEquals(expected = setOf("full"), actual = engines.keys)
        val loaded = engines.getValue("full")
        assertEquals(expected = "full", actual = loaded.entryId)
        assertNotNull(actual = loaded.actions, message = "full-manifest.yaml declares an action schema")
        assertEquals(expected = emptyList(), actual = loaded.warnings)
    }

    @Test
    fun `loads all entries of a multi entry manifest`() {
        val dir = writeProject()
        Files.writeString(
            dir.resolve("manifest.yaml"),
            """
            name: multi
            entries:
              - id: first
                schema: schema.yaml
                actions: actions.yaml
                rules:
                  - rules/a.rule
              - id: second
                schema: schema.yaml
                actions: actions.yaml
                rules:
                  - rules/b.rule
            """.trimIndent()
        )

        val engines = RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))

        assertEquals(expected = listOf("first", "second"), actual = engines.keys.toList())
        assertEquals(
            expected = listOf("a"),
            actual = engines.getValue("first").evaluate(input = MATCHING_INPUT).matches.map { it.ruleId }
        )
        assertEquals(
            expected = listOf("b"),
            actual = engines.getValue("second").evaluate(input = MATCHING_INPUT).matches.map { it.ruleId }
        )
    }

    @Test
    fun `entryId narrows the result to a single key value pair`() {
        val engines = RuleEngineBuilder.fromManifest(manifestPath = KLS_MANIFEST, entryId = "legal_affairs")

        assertEquals(expected = setOf("legal_affairs"), actual = engines.keys)
        assertEquals(expected = "legal_affairs", actual = engines.getValue("legal_affairs").entryId)
    }

    @Test
    fun `fromManifestEntry returns the entry directly`() {
        val loaded = RuleEngineBuilder.fromManifestEntry(manifestPath = FULL_MANIFEST, entryId = "full")

        assertEquals(expected = "full", actual = loaded.entryId)
    }

    @Test
    fun `unknown entryId fails and lists the available ids`() {
        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = FULL_MANIFEST, entryId = "nope")
        }

        assertEquals(expected = "nope", actual = failure.entryId)
        assertTrue(
            actual = failure.message!!.contains(other = "available ids: full"),
            message = failure.message!!
        )
    }

    @Test
    fun `evaluate matches the manually wired pipeline and honours manifest order`() {
        val loaded = RuleEngineBuilder.fromManifestEntry(manifestPath = FULL_MANIFEST, entryId = "full")

        val rentActions = actionNamesFor(loaded = loaded, inputFile = "inputs/rent-input.json")
        assertTrue(actual = rentActions.contains(element = "label"), message = "got: $rentActions")

        val vipActions = actionNamesFor(loaded = loaded, inputFile = "inputs/vip-input.json")
        assertTrue(actual = vipActions.contains(element = "label"), message = "got: $vipActions")
        assertTrue(actual = vipActions.contains(element = "score"), message = "got: $vipActions")

        val fraudActions = actionNamesFor(loaded = loaded, inputFile = "inputs/fraud-input.json")
        assertTrue(actual = fraudActions.contains(element = "flag"), message = "got: $fraudActions")
        assertTrue(actual = fraudActions.contains(element = "score"), message = "got: $fraudActions")
    }

    @Test
    fun `rules are evaluated in manifest order then declaration order`() {
        val dir = writeProject()
        // b.rule is listed first to prove manifest order beats alphabetical order.
        Files.writeString(
            dir.resolve("manifest.yaml"),
            """
            name: order
            entries:
              - id: e
                schema: schema.yaml
                actions: actions.yaml
                rules:
                  - rules/b.rule
                  - rules/a.rule
            """.trimIndent()
        )

        val loaded = RuleEngineBuilder.fromManifestEntry(manifestPath = dir.resolve("manifest.yaml"), entryId = "e")

        assertEquals(
            expected = listOf("b", "a"),
            actual = loaded.evaluate(input = MATCHING_INPUT).matches.map { it.ruleId }
        )
    }

    @Test
    fun `includeTrace populates the decision tree`() {
        val loaded = RuleEngineBuilder.fromManifestEntry(manifestPath = FULL_MANIFEST, entryId = "full")
        val input = readInput(relativePath = "inputs/rent-input.json")

        assertNull(actual = loaded.evaluate(input = input).trace)
        assertNotNull(actual = loaded.evaluate(input = input, includeTrace = true).trace)
    }

    @Test
    fun `non matching input produces no matches`() {
        val loaded = RuleEngineBuilder.fromManifestEntry(manifestPath = FULL_MANIFEST, entryId = "full")

        val result = loaded.evaluate(input = mapOf("purpose" to "nothing to see here", "amount" to 1))

        assertTrue(actual = result.matches.isEmpty(), message = "got: ${result.matches}")
    }

    @Test
    fun `missing schema file fails naming the relative and the resolved path`() {
        val dir = writeProject()
        writeManifest(dir = dir, schema = "gone.yaml")

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(actual = failure.message!!.contains(other = "'gone.yaml' not found"), message = failure.message!!)
        assertTrue(
            actual = failure.message!!.contains(other = dir.resolve("gone.yaml").toString()),
            message = failure.message!!
        )
        assertEquals(expected = "e", actual = failure.entryId)
    }

    @Test
    fun `missing actions file fails`() {
        val dir = writeProject()
        writeManifest(dir = dir, actions = "gone-actions.yaml")

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "actions file 'gone-actions.yaml' not found"),
            message = failure.message!!
        )
    }

    @Test
    fun `missing rule file fails`() {
        val dir = writeProject()
        writeManifest(dir = dir, rules = listOf("rules/gone.rule"))

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "rules file 'rules/gone.rule' not found"),
            message = failure.message!!
        )
    }

    @Test
    fun `entry without schema fails`() {
        val dir = writeProject()
        Files.writeString(
            dir.resolve("manifest.yaml"),
            """
            name: no-schema
            entries:
              - id: e
                rules:
                  - rules/a.rule
            """.trimIndent()
        )

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "declares no 'schema'"),
            message = failure.message!!
        )
    }

    @Test
    fun `entry without rules fails`() {
        val dir = writeProject()
        writeManifest(dir = dir, rules = emptyList())

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "declares no rule files"),
            message = failure.message!!
        )
    }

    @Test
    fun `manifest without entries fails`() {
        val dir = writeProject()
        Files.writeString(dir.resolve("manifest.yaml"), "name: empty\nentries: []\n")

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "contains no entries"),
            message = failure.message!!
        )
    }

    @Test
    fun `duplicate entry ids fail`() {
        val dir = writeProject()
        Files.writeString(
            dir.resolve("manifest.yaml"),
            """
            name: duplicates
            entries:
              - id: e
                schema: schema.yaml
                rules:
                  - rules/a.rule
              - id: e
                schema: schema.yaml
                rules:
                  - rules/b.rule
            """.trimIndent()
        )

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "duplicate entry ids: e"),
            message = failure.message!!
        )
    }

    @Test
    fun `path escaping the manifest directory is rejected`() {
        val dir = writeProject()
        Files.writeString(dir.parent.resolve("outside-schema.yaml"), SCHEMA_YAML)
        writeManifest(dir = dir, schema = "../outside-schema.yaml")

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "escapes base directory"),
            message = failure.message!!
        )
    }

    @Test
    fun `unreadable manifest fails`() {
        val dir = writeProject()

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("does-not-exist.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "manifest is not readable"),
            message = failure.message!!
        )
    }

    @Test
    fun `validation error fails and carries the diagnostics`() {
        val dir = writeProject()
        Files.writeString(
            dir.resolve("rules").resolve("broken.rule"),
            """
            rule "broken" {
              when
                unknownField equals "x"
              then
                label "broken"
            }
            """.trimIndent()
        )
        writeManifest(dir = dir, rules = listOf("rules/broken.rule"))

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(actual = failure.message!!.contains(other = "rule validation failed"), message = failure.message!!)
        assertTrue(
            actual = failure.diagnostics.any { it.severity == Severity.ERROR },
            message = "expected an ERROR diagnostic, got: ${failure.diagnostics}"
        )
        assertTrue(
            actual = failure.message!!.contains(other = "[ERROR]"),
            message = "diagnostics must be part of the message: ${failure.message}"
        )
    }

    @Test
    fun `unparseable rule file fails`() {
        val dir = writeProject()
        Files.writeString(dir.resolve("rules").resolve("garbage.rule"), "this is not a rule")
        writeManifest(dir = dir, rules = listOf("rules/garbage.rule"))

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "rule file 'rules/garbage.rule' could not be parsed"),
            message = failure.message!!
        )
    }

    @Test
    fun `invalid schema file fails naming the schema`() {
        val dir = writeProject()
        Files.writeString(
            dir.resolve("bad-schema.yaml"),
            """
            schema: bad
            fields:
              p:
                type: no-such-type
            """.trimIndent()
        )
        writeManifest(dir = dir, schema = "bad-schema.yaml")

        val failure = assertFailsWithBuildException {
            RuleEngineBuilder.fromManifest(manifestPath = dir.resolve("manifest.yaml"))
        }

        assertTrue(
            actual = failure.message!!.contains(other = "field schema 'bad-schema.yaml' could not be loaded"),
            message = failure.message!!
        )
        assertNotNull(actual = failure.cause, message = "the underlying loader failure must be kept as cause")
    }

    @Test
    fun `warnings are reported without failing the build`() {
        val dir = writeProject()
        // An undeclared root in a multi-segment path stays permissive and only yields a WARNING.
        Files.writeString(
            dir.resolve("rules").resolve("warning.rule"),
            """
            rule "warning" {
              when
                p equals "x" and sum(undeclaredRoot.amount) > 1
              then
                label "warning"
            }
            """.trimIndent()
        )
        writeManifest(dir = dir, rules = listOf("rules/warning.rule"))

        val loaded = RuleEngineBuilder.fromManifestEntry(manifestPath = dir.resolve("manifest.yaml"), entryId = "e")

        assertTrue(actual = loaded.warnings.isNotEmpty(), message = "expected at least one warning")
        assertFalse(actual = loaded.warnings.any { it.severity == Severity.ERROR })
    }

    @Test
    fun `shortCircuitByOutput is passed to the engine`() {
        val engines = RuleEngineBuilder.fromManifest(manifestPath = FULL_MANIFEST, shortCircuitByOutput = true)

        // The rent input only matches one rule, so the result stays the same; this asserts the flag
        // is accepted and the engine remains usable.
        val result = engines.getValue("full").evaluate(input = readInput(relativePath = "inputs/rent-input.json"))
        assertTrue(actual = result.matches.isNotEmpty())
    }

    private fun assertFailsWithBuildException(block: () -> Unit): RuleEngineBuildException =
        runCatching(block).fold(
            onSuccess = { throw AssertionError("expected RuleEngineBuildException, but the call succeeded") },
            onFailure = { throwable ->
                throwable as? RuleEngineBuildException
                    ?: throw AssertionError("expected RuleEngineBuildException, got $throwable")
            }
        )

    private fun actionNamesFor(loaded: LoadedRuleEngine, inputFile: String): List<String> =
        loaded.evaluate(input = readInput(relativePath = inputFile))
            .matches
            .flatMap { match -> match.actions.map { it.name } }

    @Suppress("UNCHECKED_CAST")
    private fun readInput(relativePath: String): Map<String, Any?> {
        val json = Files.readString(RESOURCES.resolve(relativePath))
        return JacksonUtil.jsonMapper.readValue(json, Map::class.java) as Map<String, Any?>
    }

    /** Creates a temp project with a schema, an action schema and the rule files `a.rule`/`b.rule`. */
    private fun writeProject(): Path {
        val dir = Files.createTempDirectory("rule-engine-builder")
        val rulesDir = Files.createDirectories(dir.resolve("rules"))

        Files.writeString(dir.resolve("schema.yaml"), SCHEMA_YAML)
        Files.writeString(dir.resolve("actions.yaml"), ACTIONS_YAML)
        Files.writeString(rulesDir.resolve("a.rule"), ruleFile(id = "a"))
        Files.writeString(rulesDir.resolve("b.rule"), ruleFile(id = "b"))

        return dir
    }

    private fun writeManifest(
        dir: Path,
        schema: String? = "schema.yaml",
        actions: String? = "actions.yaml",
        rules: List<String> = listOf("rules/a.rule"),
    ) {
        Files.writeString(
            dir.resolve("manifest.yaml"),
            buildString {
                appendLine("name: builder-test")
                appendLine("entries:")
                appendLine("  - id: e")
                schema?.let { appendLine("    schema: $it") }
                actions?.let { appendLine("    actions: $it") }
                if (rules.isNotEmpty()) {
                    appendLine("    rules:")
                    rules.forEach { appendLine("      - $it") }
                }
            }
        )
    }

    private fun ruleFile(id: String): String =
        """
        rule "$id" {
          when
            p equals "x"
          then
            label "$id"
        }
        """.trimIndent()

    private companion object {
        val RESOURCES: Path = Path.of("src/test/resources")
        val FULL_MANIFEST: Path = RESOURCES.resolve("full-manifest.yaml")
        val KLS_MANIFEST: Path = RESOURCES.resolve("kls/kls_manifest.yaml")
        val MATCHING_INPUT: Map<String, Any?> = mapOf("p" to "x")

        val SCHEMA_YAML: String = """
            schema: builder-test
            fields:
              p:
                type: text
                normalizers:
                  - trim
                operators:
                  - equals
        """.trimIndent()

        val ACTIONS_YAML: String = """
            actions:
              label:
                argTypes: [string]
        """.trimIndent()
    }
}
