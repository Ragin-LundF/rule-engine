package ruleengine.cli

import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the CLIs report for a rule with an `else` branch.
 *
 * The branch has to reach the JSON: without it every entry in `matches` reads as a rule whose
 * condition held, which is exactly what an `else` branch contradicts.
 */
class CliElseBranchTest {

    private val schema = Path.of("src/test/resources/sample-schema.yaml").toAbsolutePath().toString()

    @Test
    fun `evaluate reports the else branch for a rule whose condition did not hold`() {
        val out = StringBuilder()
        val rulesDirectory = rulesDirectoryWith(rule = TIERED_RULE)
        val inputFile = inputFileWith(json = """{"purpose": "coffee", "amount": "5"}""")

        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--schema", schema,
                "--rules", rulesDirectory.toString(),
                "--input-file", inputFile.toString(),
                "--format", "json",
            ),
            out = out,
        )

        assertEquals(expected = 0, actual = exit)
        val matches = JacksonUtil.jsonMapper.readTree(out.toString()).get("matches")
        assertEquals(expected = 1, actual = matches.size())
        assertEquals(expected = "tier", actual = matches[0].get("ruleId").asString())
        assertEquals(expected = "else", actual = matches[0].get("branch").asString())
        assertEquals(expected = "low", actual = matches[0].get("actions")[0].get("arguments")[0].asString())
    }

    @Test
    fun `evaluate reports the then branch for the same rule when the condition holds`() {
        val out = StringBuilder()
        val rulesDirectory = rulesDirectoryWith(rule = TIERED_RULE)
        val inputFile = inputFileWith(json = """{"purpose": "rent", "amount": "2000"}""")

        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--schema", schema,
                "--rules", rulesDirectory.toString(),
                "--input-file", inputFile.toString(),
                "--format", "json",
            ),
            out = out,
        )

        assertEquals(expected = 0, actual = exit)
        val matches = JacksonUtil.jsonMapper.readTree(out.toString()).get("matches")
        assertEquals(expected = "then", actual = matches[0].get("branch").asString())
        assertEquals(expected = "high", actual = matches[0].get("actions")[0].get("arguments")[0].asString())
    }

    @Test
    fun `validate accepts a rule with an else branch`() {
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(
            args = arrayOf("--schema", schema, "--rules", rulesDirectoryWith(rule = TIERED_RULE).toString()),
            out = out,
        )

        assertEquals(expected = 0, actual = exit, message = out.toString())
    }

    @Test
    fun `validate rejects an unknown field in an else set clause`() {
        val out = StringBuilder()
        val rulesDirectory = rulesDirectoryWith(
            rule = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  else
                    set level = nosuchfield
                }
            """.trimIndent()
        )

        val exit = ValidatorCli.runCli(
            args = arrayOf("--schema", schema, "--rules", rulesDirectory.toString()),
            out = out,
        )

        assertEquals(expected = 1, actual = exit)
        assertTrue(
            actual = out.toString().contains(other = "nosuchfield"),
            message = "unexpected output: $out"
        )
    }

    @Test
    fun `validate rejects an empty else block`() {
        val out = StringBuilder()
        val rulesDirectory = rulesDirectoryWith(
            rule = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  else
                }
            """.trimIndent()
        )

        val exit = ValidatorCli.runCli(
            args = arrayOf("--schema", schema, "--rules", rulesDirectory.toString()),
            out = out,
        )

        // Exit 3, not 1: an empty block is a parse failure, which the CLI reports as a thrown error
        // rather than as a diagnostic.
        assertEquals(expected = 3, actual = exit)
        assertTrue(
            actual = out.toString().contains(other = "Empty 'else' block"),
            message = "unexpected output: $out"
        )
    }

    private fun rulesDirectoryWith(rule: String): Path {
        val root = Files.createTempDirectory("else-cli-rules")
        Files.writeString(root.resolve("tier.rule"), rule)
        return root
    }

    private fun inputFileWith(json: String): Path {
        val file = Files.createTempFile("else-cli-input", ".json")
        Files.writeString(file, json)
        return file
    }

    private companion object {
        val TIERED_RULE: String = """
            rule "tier" {
              description "A payment of at least 1000 is high tier, anything else is low tier."
              when
                amount >= 1000
              then
                label "high"
              else
                label "low"
            }
        """.trimIndent()
    }
}
