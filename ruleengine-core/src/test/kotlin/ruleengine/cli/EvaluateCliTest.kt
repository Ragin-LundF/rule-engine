package ruleengine.cli

import ruleengine.core.io.FileInputSupport
import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvaluateCliTest {
    @Test
    fun `evaluate input produces matches and decisionTree when trace enabled`() {
        val out = StringBuilder()
        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--schema",
                "src/test/resources/sample-schema.yaml",
                "--rules",
                "src/test/resources/rules",
                "--input-file",
                "src/test/resources/sample-input.json",
                "--trace",
                "--format",
                "json"
            ), out = out
        )
        assertEquals(expected = 0, actual = exit)
        val mapper = JacksonUtil.jsonMapper
        val node = mapper.readTree(out.toString())
        assertNotNull(actual = node.get("matches"))
        assertNotNull(actual = node.get("decisionTree"))
    }

    /**
     * An unscoped run must keep the shape consumers already parse, so the per-member block is only
     * written when the entry actually declares a scope.
     */
    @Test
    fun `an unscoped run writes no members block`() {
        val node = runManifest(manifest = "src/test/resources/manifest.yaml", entry = null)

        assertEquals(expected = null, actual = node.get("members"))
        assertEquals(
            expected = null,
            actual = node.get("matches").firstOrNull()?.get("scopeMember"),
            message = "a match outside a scoped run has no member to name"
        )
    }

    @Test
    fun `a scoped run writes one members entry per collection member`() {
        val node = runManifest(
            manifest = "src/test/resources/scoped-accounts/manifest.yaml",
            entry = "account-review",
            input = "src/test/resources/scoped-accounts/input.json"
        )

        val members = assertNotNull(actual = node.get("members"))
        assertEquals(expected = 2, actual = members.size())
        assertEquals(expected = "acc-1", actual = members[0].get("key").asString())
        assertEquals(expected = "acc-2", actual = members[1].get("key").asString())
        assertEquals(
            expected = "acc-1",
            actual = node.get("matches")[0].get("scopeMember").asString(),
            message = "the flat list names the member each match came from"
        )
    }

    private fun runManifest(
        manifest: String,
        entry: String?,
        input: String = "src/test/resources/sample-input.json",
    ): tools.jackson.databind.JsonNode {
        val out = StringBuilder()
        val args = buildList {
            add("--manifest")
            add(manifest)
            entry?.let {
                add("--entry")
                add(it)
            }
            add("--input-file")
            add(input)
            add("--format")
            add("json")
        }
        val exit = EvaluateCli.runCli(args = args.toTypedArray(), out = out)

        assertEquals(expected = 0, actual = exit, message = out.toString())
        return JacksonUtil.jsonMapper.readTree(out.toString())
    }

    @Test
    fun `evaluate input rejects oversized input file`() {
        val out = StringBuilder()
        val inputFile = Files.createTempFile("oversized-input", ".json")
        Files.writeString(inputFile, oversizedInput())

        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--schema",
                "src/test/resources/sample-schema.yaml",
                "--rules",
                "src/test/resources/rules",
                "--input-file",
                inputFile.toString(),
                "--format",
                "json"
            ), out = out
        )

        assertEquals(expected = 3, actual = exit)
        assertTrue(actual = out.toString().contains(other = "exceeds limit"))
    }

    private fun oversizedInput(): String {
        return "{" +
            "\"purpose\":\"" +
            "a".repeat(FileInputSupport.DEFAULT_MAX_BYTES.toInt()) +
            "\"}"
    }
}

