package ruleengine.cli

import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the CLIs report for a rule with a `not_exists` branch.
 *
 * The branch and the verdict both have to reach the JSON. Without them a consumer cannot tell a rule
 * that decided against a record from one the record gave no data to answer — which is the entire point
 * of declaring the branch.
 */
class CliNotExistsBranchTest {

    private val schema = Path.of("src/test/resources/sample-schema.yaml").toAbsolutePath().toString()

    @Test
    fun `evaluate reports the not_exists branch when the field the condition reads is absent`() {
        val out = StringBuilder()

        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--schema", schema,
                "--rules", rulesDirectoryWith(rule = THREE_BRANCH_RULE).toString(),
                "--input-file", inputFileWith(json = """{"purpose": "coffee"}""").toString(),
                "--format", "json",
            ),
            out = out,
        )

        assertEquals(expected = 0, actual = exit)
        val matches = JacksonUtil.jsonMapper.readTree(out.toString()).get("matches")
        assertEquals(expected = 1, actual = matches.size())
        assertEquals(expected = "not_exists", actual = matches[0].get("branch").asString())
        assertEquals(expected = "unknown", actual = matches[0].get("actions")[0].get("arguments")[0].asString())
    }

    @Test
    fun `evaluate still reports else when the field is there and the condition is false`() {
        val out = StringBuilder()

        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--schema", schema,
                "--rules", rulesDirectoryWith(rule = THREE_BRANCH_RULE).toString(),
                "--input-file", inputFileWith(json = """{"purpose": "coffee", "amount": "5"}""").toString(),
                "--format", "json",
            ),
            out = out,
        )

        assertEquals(expected = 0, actual = exit)
        val matches = JacksonUtil.jsonMapper.readTree(out.toString()).get("matches")
        assertEquals(expected = "else", actual = matches[0].get("branch").asString())
    }

    @Test
    fun `the trace carries the undecided verdict and the branch it selected`() {
        val out = StringBuilder()

        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--schema", schema,
                "--rules", rulesDirectoryWith(rule = THREE_BRANCH_RULE).toString(),
                "--input-file", inputFileWith(json = """{"purpose": "coffee"}""").toString(),
                "--trace",
                "--format", "json",
            ),
            out = out,
        )

        assertEquals(expected = 0, actual = exit)
        val ruleNode = JacksonUtil.jsonMapper.readTree(out.toString())
            .get("decisionTree").get("root").get("children")[0]
        assertEquals(expected = "UNKNOWN", actual = ruleNode.get("verdict").asString())
        assertEquals(expected = "NOT_EXISTS", actual = ruleNode.get("branch").asString())
        assertEquals(expected = false, actual = ruleNode.get("result").asBoolean())
    }

    @Test
    fun `validate accepts a rule with a not_exists branch`() {
        val out = StringBuilder()

        val exit = ValidatorCli.runCli(
            args = arrayOf("--schema", schema, "--rules", rulesDirectoryWith(rule = THREE_BRANCH_RULE).toString()),
            out = out,
        )

        assertEquals(expected = 0, actual = exit, message = out.toString())
    }

    @Test
    fun `validate rejects an unknown field in a not_exists set clause`() {
        val out = StringBuilder()
        val rulesDirectory = rulesDirectoryWith(
            rule = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  not_exists
                    set level = nosuchfield
                }
            """.trimIndent()
        )

        val exit = ValidatorCli.runCli(
            args = arrayOf("--schema", schema, "--rules", rulesDirectory.toString()),
            out = out,
        )

        assertEquals(expected = 1, actual = exit)
        assertTrue(actual = out.toString().contains(other = "nosuchfield"), message = "unexpected output: $out")
    }

    private fun rulesDirectoryWith(rule: String): Path {
        val root = Files.createTempDirectory("not-exists-cli-rules")
        Files.writeString(root.resolve("tier.rule"), rule)
        return root
    }

    private fun inputFileWith(json: String): Path {
        val file = Files.createTempFile("not-exists-cli-input", ".json")
        Files.writeString(file, json)
        return file
    }

    private companion object {
        val THREE_BRANCH_RULE: String = """
            rule "tier" {
              description "A payment of at least 1000 is high tier, a smaller one low, no amount neither."
              when
                amount >= 1000
              then
                label "high"
              else
                label "low"
              not_exists
                label "unknown"
            }
        """.trimIndent()
    }
}
