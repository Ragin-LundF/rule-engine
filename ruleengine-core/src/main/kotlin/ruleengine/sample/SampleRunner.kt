package ruleengine.sample

import ruleengine.builder.RuleEngineBuilder
import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import java.nio.file.Path

object SampleRunner {
    @JvmStatic
    @Suppress("ReturnCount")
    fun main(args: Array<String>) {
        // determine manifest path (use test manifest by default)
        val manifestPath = Path.of("src/test/resources/full-manifest.yaml")

        val loaded = runCatching {
            RuleEngineBuilder.fromManifest(manifestPath = manifestPath).values.first()
        }.getOrElse { ex ->
            System.err.println(ex.message)
            return
        }

        val inputRelative = if (args.isNotEmpty()) args[0] else "inputs/rent-input.json"
        val inputPath = manifestPath.parent.resolve(inputRelative)

        val inputJson = runCatching {
            Files.readString(inputPath)
        }.getOrElse { ex ->
            System.err.println("Failed to read input file ${inputPath}: ${ex.message}")
            return
        }

        val inputMap = runCatching {
            @Suppress("UNCHECKED_CAST")
            JacksonUtil.jsonMapper.readValue(inputJson, Map::class.java) as Map<String, Any?>
        }.getOrElse { ex ->
            System.err.println("Failed to parse input JSON: ${ex.message}")
            return
        }

        val result = loaded.evaluate(input = inputMap, includeTrace = true)

        // build simple output structure and print pretty JSON
        val outputMap = mutableMapOf<String, Any?>()
        outputMap["matches"] = result.matches.map { match ->
            mapOf(
                "ruleId" to match.ruleId,
                "actions" to match.actions.map { a -> mapOf("name" to a.name, "arguments" to a.arguments) }
            )
        }
        outputMap["decisionTree"] = result.trace

        val out = runCatching {
            JacksonUtil.jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(outputMap)
        }.fold(
            onSuccess = { it },
            onFailure = { ex ->
                System.err.println("Failed to serialize result: ${ex.message}")
                return
            }
        )

        println(out)
    }
}
