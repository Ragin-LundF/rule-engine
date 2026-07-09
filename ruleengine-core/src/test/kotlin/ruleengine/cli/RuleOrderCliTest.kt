package ruleengine.cli

import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleOrderCliTest {
    @Test
    fun `manifest mode evaluates rules in manifest then declaration order`() {
        val dir = Files.createTempDirectory("rule-order-cli")
        val rulesDir = Files.createDirectories(dir.resolve("rules"))

        Files.writeString(
            dir.resolve("schema.yaml"),
            """
            schema: order-test
            fields:
              p:
                type: text
                normalizers:
                  - trim
                operators:
                  - equals
            """.trimIndent()
        )
        Files.writeString(
            dir.resolve("actions.yaml"),
            """
            actions:
              label:
                argTypes: [string]
            """.trimIndent()
        )
        // Two rules per file; all match the same input.
        Files.writeString(rulesDir.resolve("a-file.rule"), ruleFile(ids = listOf("a1", "a2")))
        Files.writeString(rulesDir.resolve("b-file.rule"), ruleFile(ids = listOf("b1", "b2")))
        Files.writeString(dir.resolve("input.json"), """{"p":"x"}""")

        // b-file is listed BEFORE a-file to prove manifest order wins over alphabetical/FS order.
        Files.writeString(
            dir.resolve("manifest.yaml"),
            """
            name: order-test
            entries:
              - id: e
                schema: schema.yaml
                actions: actions.yaml
                rules:
                  - rules/b-file.rule
                  - rules/a-file.rule
            """.trimIndent()
        )

        val out = StringBuilder()
        val exit = EvaluateCli.runCli(
            args = arrayOf(
                "--manifest", dir.resolve("manifest.yaml").toString(),
                "--input-file", dir.resolve("input.json").toString(),
                "--format", "json"
            ),
            out = out
        )

        assertEquals(expected = 0, actual = exit, message = out.toString())
        val matchedIds = matchedRuleIds(json = out.toString())
        assertEquals(expected = listOf("b1", "b2", "a1", "a2"), actual = matchedIds)
    }

    private fun ruleFile(ids: List<String>): String {
        return ids.joinToString(separator = "\n\n") { id ->
            """
            rule "$id" {
              when
                p equals "x"
              then
                label "$id"
            }
            """.trimIndent()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun matchedRuleIds(json: String): List<String> {
        val root = JacksonUtil.jsonMapper.readValue(json, Map::class.java) as Map<String, Any?>
        val matches = root["matches"] as List<Map<String, Any?>>
        return matches.map { it["ruleId"] as String }
    }
}
