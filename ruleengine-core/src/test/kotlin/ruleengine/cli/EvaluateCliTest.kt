package ruleengine.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import ruleengine.jackson.JacksonUtil

class EvaluateCliTest {
    @Test
    fun `evaluate input produces matches and decisionTree when trace enabled`() {
        val out = StringBuilder()
        val exit = EvaluateCli.runCli(arrayOf("--schema", "src/test/resources/sample-schema.yaml", "--rules", "src/test/resources/rules", "--input-file", "src/test/resources/sample-input.json", "--trace", "--format", "json"), out)
        assertEquals(0, exit)
        val mapper = JacksonUtil.jsonMapper
        val node = mapper.readTree(out.toString())
        assertNotNull(node.get("matches"))
        assertNotNull(node.get("decisionTree"))
    }
}

