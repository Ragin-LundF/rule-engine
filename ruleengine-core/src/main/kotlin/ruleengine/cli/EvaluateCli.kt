package ruleengine.cli

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.jackson.JacksonUtil
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * CLI to evaluate a single input JSON against a schema + rules directory.
 * Usage:
 *   --schema <schema.yaml> --rules <rules-dir> --input-file <input.json> [--trace] [--format json|pretty-json]
 */
object EvaluateCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val exit = runCli(args = args)
        exitProcess(status = exit)
    }

    fun runCli(args: Array<String>, out: Appendable = System.out): Int {
        return runCatching {
            // simple args parser that supports flags (no value) and key value pairs
            val kv = mutableMapOf<String, String?>()
            var i = 0
            while (i < args.size) {
                val k = args[i]
                val v = if (i + 1 < args.size && !args[i + 1].startsWith(prefix = "--")) {
                    args[i + 1]
                } else {
                    null
                }

                kv[k] = v
                i += if (v != null) 2 else 1
            }

            val schemaPath = kv["--schema"] ?: return usage(out = out)
            val rulesPath = kv["--rules"] ?: return usage(out = out)
            val inputFile = kv["--input-file"] ?: return usage(out = out)
            val trace = kv.containsKey("--trace")
            val format = kv["--format"]?.lowercase()

            val schema = FieldSchemaLoader.load(Path.of(schemaPath))

            val rulesDir = Path.of(rulesPath)
            if (!Files.exists(rulesDir) || !Files.isDirectory(rulesDir)) {
                out.append("Rules path is not a directory: $rulesPath\n")
                return 2
            }

            val ruleFiles = Files.walk(rulesDir).filter {
                Files.isRegularFile(it) && it.toString().endsWith(".rule")
            }.toList()
            val asts = ruleFiles.flatMap { f -> Parser(input = Files.readString(f)).parseRules() }

            val validation = Validator.validate(asts = asts, schema = schema)
            if (!validation.isValid) {
                out.append("Validation failed: ${validation.diagnostics}\n")
                return 1
            }

            val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
            val engine = RuleEngine(compiledRules = compiled, schema = schema)

            val mapper = JacksonUtil.jsonMapper
            val inputJson = Files.readString(Path.of(inputFile))

            @Suppress("UNCHECKED_CAST")
            val inputMap: Map<String, Any?> = mapper.readValue(inputJson, Map::class.java) as Map<String, Any?>

            val ctx = RuleContext.of(entries = inputMap.entries.map {
                it.key to it.value
            }.toTypedArray())
            val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
            val result = engine.evaluate(prepared = prepared, includeTrace = trace)

            val outMap = mutableMapOf<String, Any?>()
            outMap["matches"] = result.matches.map { m ->
                mapOf(
                    "ruleId" to m.ruleId,
                    "actions" to m.actions.map { a -> mapOf("name" to a.name, "arguments" to a.arguments) })
            }
            if (trace && result.trace != null) {
                // result.trace is DecisionTree
                outMap["decisionTree"] = result.trace
            }

            if (format == "pretty-json") {
                out.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(outMap))
            } else {
                out.append(mapper.writeValueAsString(outMap))
            }
            out.append("\n")
            0
        }.fold(
            onSuccess = { it },
            onFailure = {
                out.append("Error: ${it.message}\n")
                3
            }
        )
    }

    private fun usage(out: Appendable): Int {
        out.append("Usage: --schema <schema.yaml> ")
            .append("--rules <rules-dir> ")
            .append("--input-file <input.json> ")
            .append("[--trace] [--format json|pretty-json]\n")
        return 2
    }
}

