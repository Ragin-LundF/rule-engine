package ruleengine.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import ruleengine.cli.ValidatorCli
import java.nio.file.Path

class ValidatorCliTest {
    @Test
    fun `cli validates sample rules successfully`() {
        val schema = Path.of("src/test/resources/sample-schema.yaml").toAbsolutePath().toString()
        val rules = Path.of("src/test/resources/rules").toAbsolutePath().toString()
        val exit = ValidatorCli.runCli(arrayOf("--schema", schema, "--rules", rules))
        assertEquals(expected = 0, actual = exit)
    }
}

