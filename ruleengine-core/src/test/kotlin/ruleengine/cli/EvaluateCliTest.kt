package ruleengine.cli

import ruleengine.jackson.JacksonUtil
import ruleengine.core.io.FileInputSupport
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

