package ruleengine.cli

import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ValidatorCli --manifest`: validating an entry the way the engine loads it.
 *
 * This is the mode an assistant checking its own generated project should use, and the reason it exists:
 * directory mode never sees the action schema, and cannot know which rule file comes before which — so
 * neither an unknown action nor a variable that crosses files is checked there.
 */
class ValidatorCliManifestTest {

    @Test
    fun `a valid entry passes`() {
        val root = project()
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(
            args = arrayOf("--manifest", root.resolve("manifest.yaml").toString()),
            out = out,
        )

        assertEquals(expected = 0, actual = exit, message = out.toString())
        assertTrue(actual = out.toString().contains(other = "Validation OK"), message = out.toString())
    }

    /** The gap directory mode leaves: the variable's writer is in another file. */
    @Test
    fun `a variable that crosses rule files resolves`() {
        val root = project(
            second = """
                rule "tiers" {
                  description "d"
                  when
                    ${'$'}turnover >= 100
                  then
                    label "vip"
                }
            """.trimIndent()
        )

        val exit = ValidatorCli.runCli(
            args = arrayOf("--manifest", root.resolve("manifest.yaml").toString()),
            out = StringBuilder(),
        )

        assertEquals(expected = 0, actual = exit)
    }

    @Test
    fun `a forward reference across files fails, naming the file it is in`() {
        val root = project(
            first = """
                rule "tiers" {
                  description "d"
                  when
                    ${'$'}turnover >= 100
                  then
                    label "vip"
                }
            """.trimIndent(),
            second = """
                rule "totals" {
                  description "d"
                  when
                    amount > 0
                  then
                    set turnover = amount
                }
            """.trimIndent(),
        )
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(
            args = arrayOf("--manifest", root.resolve("manifest.yaml").toString(), "--format", "json"),
            out = out,
        )

        assertEquals(expected = 1, actual = exit)
        val diagnostics = JacksonUtil.jsonMapper.readTree(out.toString()).get("diagnostics")
        assertEquals(expected = 1, actual = diagnostics.size())
        assertTrue(
            actual = diagnostics[0].get("message").asString().contains(other = "unknown variable"),
            message = out.toString(),
        )
        assertTrue(
            actual = diagnostics[0].get("file").asString().endsWith(suffix = "first.rule"),
            message = "the diagnostic must name its file: $out",
        )
    }

    /** Directory mode without `--actions` cannot catch this; manifest mode always does. */
    @Test
    fun `an unknown action fails because the manifest names the action schema`() {
        val root = project(
            first = """
                rule "totals" {
                  description "d"
                  when
                    amount > 0
                  then
                    nosuchaction "x"
                }
            """.trimIndent()
        )
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(
            args = arrayOf("--manifest", root.resolve("manifest.yaml").toString()),
            out = out,
        )

        assertEquals(expected = 1, actual = exit)
        assertTrue(actual = out.toString().contains(other = "nosuchaction"), message = out.toString())
    }

    @Test
    fun `an entry id that does not exist is reported rather than thrown`() {
        val root = project()
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(
            args = arrayOf(
                "--manifest", root.resolve("manifest.yaml").toString(),
                "--entry", "nosuchentry",
            ),
            out = out,
        )

        assertEquals(expected = 3, actual = exit)
        assertTrue(actual = out.toString().startsWith(prefix = "Error:"), message = out.toString())
    }

    @Test
    fun `--entry selects the named entry`() {
        val root = project()
        val exit = ValidatorCli.runCli(
            args = arrayOf("--manifest", root.resolve("manifest.yaml").toString(), "--entry", "orders"),
            out = StringBuilder(),
        )

        assertEquals(expected = 0, actual = exit)
    }

    @Test
    fun `directory mode checks action names once --actions is given`() {
        val root = project(
            first = """
                rule "totals" {
                  description "d"
                  when
                    amount > 0
                  then
                    nosuchaction "x"
                }
            """.trimIndent()
        )

        val withoutActions = ValidatorCli.runCli(
            args = arrayOf(
                "--schema", root.resolve("schema.yaml").toString(),
                "--rules", root.resolve("rules").toString(),
            ),
            out = StringBuilder(),
        )
        val withActions = ValidatorCli.runCli(
            args = arrayOf(
                "--schema", root.resolve("schema.yaml").toString(),
                "--rules", root.resolve("rules").toString(),
                "--actions", root.resolve("actions.yaml").toString(),
            ),
            out = StringBuilder(),
        )

        assertEquals(expected = 0, actual = withoutActions, message = "action names are unchecked without --actions")
        assertEquals(expected = 1, actual = withActions)
    }

    /**
     * The text output is what a person — or a rule-generating assistant following RULE-SPEC §10 — reads.
     * One diagnostic per line, position first, so it can be acted on without parsing JSON.
     */
    @Test
    fun `each diagnostic is its own readable line`() {
        val root = project(
            first = """
                rule "totals" {
                  description "d"
                  when
                    nosuchfield > 0
                  then
                    label "x"
                }
            """.trimIndent()
        )
        val out = StringBuilder()

        ValidatorCli.runCli(args = arrayOf("--manifest", root.resolve("manifest.yaml").toString()), out = out)

        val lines = out.toString().trim().lines()
        assertEquals(expected = "Validation failed:", actual = lines.first())
        assertEquals(expected = 2, actual = lines.size, message = out.toString())
        assertTrue(
            actual = lines[1].startsWith(prefix = "  [ERROR] rules/first.rule:4:"),
            message = "expected severity then position then message, got: ${lines[1]}",
        )
        assertTrue(actual = lines[1].endsWith(suffix = "Unknown field 'nosuchfield' in condition"))
    }

    @Test
    fun `a suggestion is shown after the message`() {
        val root = project(
            first = """
                rule "totals" {
                  description "d"
                  when
                    amonut > 0
                  then
                    label "x"
                }
            """.trimIndent()
        )
        val out = StringBuilder()

        ValidatorCli.runCli(args = arrayOf("--manifest", root.resolve("manifest.yaml").toString()), out = out)

        assertTrue(actual = out.toString().contains(other = "→ amount"), message = out.toString())
    }

    /** A clean run still shows its warnings; nothing else would ever surface them. */
    @Test
    fun `warnings are printed even when validation passes`() {
        val root = project(
            first = """
                rule "totals" {
                  when
                    amount > 0
                  then
                    label "x"
                }
            """.trimIndent()
        )
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(
            args = arrayOf("--manifest", root.resolve("manifest.yaml").toString()),
            out = out,
        )

        assertEquals(expected = 0, actual = exit)
        assertTrue(actual = out.toString().contains(other = "Validation OK"))
        assertTrue(actual = out.toString().contains(other = "[WARNING]"), message = out.toString())
        assertTrue(actual = out.toString().contains(other = "has no description"), message = out.toString())
    }

    @Test
    fun `usage is reported when neither mode is fully specified`() {
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(args = arrayOf("--rules", "somewhere"), out = out)

        assertEquals(expected = 2, actual = exit)
        assertTrue(actual = out.toString().contains(other = "--manifest"), message = out.toString())
    }

    /**
     * A project on disk, so the CLI is exercised through the real manifest loader.
     *
     * The two rule files are named `first.rule` and `second.rule` and listed in that order, which is
     * what makes "an earlier file" mean something the tests can assert on.
     */
    private fun project(
        first: String = """
            rule "totals" {
              description "d"
              when
                amount > 0
              then
                set turnover = amount
            }
        """.trimIndent(),
        second: String = """
            rule "labels" {
              description "d"
              when
                amount > 1
              then
                label "big"
            }
        """.trimIndent(),
    ): Path {
        val root = Files.createTempDirectory("validator-cli-manifest")
        Files.createDirectory(root.resolve("rules"))
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                name: cli-test
                entries:
                  - id: orders
                    schema: schema.yaml
                    actions: actions.yaml
                    rules:
                      - rules/first.rule
                      - rules/second.rule
            """.trimIndent(),
        )
        Files.writeString(
            root.resolve("schema.yaml"),
            """
                schema: orders
                fields:
                  amount:
                    type: decimal
                    operators: [gt, gte, equals]
            """.trimIndent(),
        )
        Files.writeString(
            root.resolve("actions.yaml"),
            """
                actions:
                  label:
                    argTypes: [string]
            """.trimIndent(),
        )
        Files.writeString(root.resolve("rules/first.rule"), first)
        Files.writeString(root.resolve("rules/second.rule"), second)
        return root
    }
}
