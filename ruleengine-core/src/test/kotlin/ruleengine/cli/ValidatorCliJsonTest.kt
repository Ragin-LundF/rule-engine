package ruleengine.cli

import ruleengine.jackson.JacksonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ValidatorCliJsonTest {
    @Test
    fun `cli json output contains diagnostics and ok flag`() {
        val out = StringBuilder()
        val exit = ValidatorCli.runCli(
            args = arrayOf(
                "--schema",
                "src/test/resources/sample-schema.yaml",
                "--rules",
                "src/test/resources/rules",
                "--format",
                "json"
            ), out = out
        )
        assertEquals(expected = 0, actual = exit)
        val mapper = JacksonUtil.jsonMapper
        val node = mapper.readTree(out.toString())
        assertNotNull(actual = node.get("diagnostics"))
        assertNotNull(actual = node.get("ok"))
        assertNotNull(actual = node.get("exitCode"))
        assertEquals(expected = 0, actual = node.get("exitCode").asInt())
    }
}

