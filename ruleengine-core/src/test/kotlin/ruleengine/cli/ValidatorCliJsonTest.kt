package ruleengine.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import ruleengine.jackson.JacksonUtil

class ValidatorCliJsonTest {
    @Test
    fun `cli json output contains diagnostics and ok flag`() {
        val out = StringBuilder()
        val exit = ValidatorCli.runCli(arrayOf("--schema", "src/test/resources/sample-schema.yaml", "--rules", "src/test/resources/rules", "--format", "json"), out)
        assertEquals(expected = 0, actual = exit)
        val mapper = JacksonUtil.jsonMapper
        val node = mapper.readTree(out.toString())
        assertNotNull(node.get("diagnostics"))
        assertNotNull(node.get("ok"))
        assertNotNull(node.get("exitCode"))
        assertEquals(0, node.get("exitCode").asInt())
    }
}

