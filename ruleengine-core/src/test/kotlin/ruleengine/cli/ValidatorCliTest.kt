package ruleengine.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatorCliTest {
    @Test
    fun `cli validates nested rules directory successfully`() {
        val schema = Path.of("src/test/resources/sample-schema.yaml").toAbsolutePath().toString()
        val rulesDirectory = createNestedRulesDirectory()
        val exit = ValidatorCli.runCli(arrayOf("--schema", schema, "--rules", rulesDirectory.toString()))
        assertEquals(expected = 0, actual = exit)
    }

    @Test
    fun `cli rejects oversized rule files`() {
        val out = StringBuilder()
        val schema = Path.of("src/test/resources/sample-schema.yaml").toAbsolutePath().toString()
        val rulesDirectory = createOversizedRulesDirectory()

        val exit = ValidatorCli.runCli(
            args = arrayOf(
                "--schema",
                schema,
                "--rules",
                rulesDirectory.toString()
            ),
            out = out
        )

        assertEquals(expected = 3, actual = exit)
        assertTrue(actual = out.toString().contains(other = "exceeds limit"))
    }

    private fun createNestedRulesDirectory(): Path {
        val root = Files.createTempDirectory("validator-cli-rules")
        val nested = Files.createDirectories(root.resolve("nested"))
        Files.writeString(nested.resolve("nested.rule"), sampleRule())
        return root
    }

    private fun sampleRule(): String {
        return """
            rule "nested-rent" {
              when
                purpose contains "rent"
                and amount >= 500

              then
                label "rent"
            }
        """.trimIndent()
    }

    private fun createOversizedRulesDirectory(): Path {
        val root = Files.createTempDirectory("validator-cli-oversized-rules")
        Files.writeString(root.resolve("oversized.rule"), oversizedRule())
        return root
    }

    private fun oversizedRule(): String {
        return "rule \"oversized\" { when purpose contains \"" +
            "a".repeat(ruleengine.core.io.FileInputSupport.DEFAULT_MAX_BYTES.toInt()) +
            "\" then label \"x\" }"
    }
}

