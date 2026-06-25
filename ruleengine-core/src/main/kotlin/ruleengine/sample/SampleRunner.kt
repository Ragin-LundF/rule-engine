package ruleengine.sample

import ruleengine.jackson.JacksonUtil
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Validator
import ruleengine.compiler.Compiler
import ruleengine.core.domain.EvaluationResult
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.nio.file.Path

object SampleRunner {
    @JvmStatic
    @Suppress("LongMethod", "ReturnCount")
    fun main(args: Array<String>) {
        // determine manifest path (use test manifest by default)
        val manifestPath = Path.of("src/test/resources/full-manifest.yaml")

        val manifest = runCatching {
            ManifestLoader.load(path = manifestPath)
        }.getOrElse { ex ->
            System.err.println("Failed to load manifest at ${manifestPath}: ${ex.message}")
            return
        }

        val entry = manifest.entries.firstOrNull()
        if (entry == null) {
            System.err.println("Manifest contains no entries")
            return
        }

        val baseDir = manifestPath.parent

        val schema = runCatching {
            FieldSchemaLoader.load(path = baseDir.resolve(entry.schema!!))
        }.getOrElse { ex ->
            System.err.println("Failed to load schema: ${ex.message}")
            return
        }

        val actions = runCatching {
            ActionSchemaLoader.load(path = baseDir.resolve(entry.actions!!))
        }.getOrElse { ex ->
            System.err.println("Failed to load actions schema: ${ex.message}")
            return
        }

        val ruleAsts = entry.rules.flatMap { rel ->
            val rulePath = baseDir.resolve(rel)
            val content = java.nio.file.Files.readString(rulePath)
            Parser(input = content).parseRules()
        }

        val validationResult = Validator.validate(asts = ruleAsts, schema = schema, actions = actions)
        if (!validationResult.isValid) {
            System.err.println("Rule validation failed: ${validationResult.diagnostics}")
            return
        }

        val compiled = Compiler.compileRules(asts = ruleAsts, schema = schema)
        val engine = RuleEngine(compiledRules = compiled)

        val inputRelative = if (args.isNotEmpty()) args[0] else "inputs/rent-input.json"
        val inputPath = baseDir.resolve(inputRelative)

        val inputJson = runCatching {
            java.nio.file.Files.readString(inputPath)
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

        val ruleContext = RuleContext.of(entries = inputMap.entries.map { it.key to it.value }.toTypedArray())
        val prepared = PreparedRuleContext.prepare(ctx = ruleContext, schema = schema)

        val result = engine.evaluate(prepared = prepared, includeTrace = true)

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

