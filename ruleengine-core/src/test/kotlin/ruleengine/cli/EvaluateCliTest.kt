package ruleengine.cli

import ruleengine.jackson.JacksonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}

