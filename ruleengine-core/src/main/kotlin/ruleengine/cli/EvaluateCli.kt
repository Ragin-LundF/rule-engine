package ruleengine.cli

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.EvaluationResult
import ruleengine.core.domain.FieldSchema
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.core.io.FileInputSupport
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

    @Suppress("MagicNumber")
    fun runCli(args: Array<String>, out: Appendable = System.out): Int {
        return runCatching {
            executeCli(args = args, out = out)
        }.fold(
            onSuccess = { it },
            onFailure = {
                out.append("Error: ${it.message}\n")
                3
            }
        )
    }

    private fun executeCli(args: Array<String>, out: Appendable): Int {
        val parsedArguments = parseArguments(args = args)
        val cliOptions = readCliOptions(parsedArguments = parsedArguments, out = out) ?: return 2

        val schema = FieldSchemaLoader.load(path = Path.of(cliOptions.schemaPath))
        val rulesDirectory = Path.of(cliOptions.rulesPath)
        if (!Files.exists(rulesDirectory) || !Files.isDirectory(rulesDirectory)) {
            out.append("Rules path is not a directory: ${cliOptions.rulesPath}\n")
            return 2
        }

        val ruleAstList = loadRuleAstList(rulesDirectory = rulesDirectory)
        val validationResult = Validator.validate(asts = ruleAstList, schema = schema)
        if (!validationResult.isValid) {
            out.append("Validation failed: ${validationResult.diagnostics}\n")
            return 1
        }

        val compiledRules = Compiler.compileRules(
            asts = ruleAstList,
            schema = schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        val engine = RuleEngine(compiledRules = compiledRules, schema = schema)

        val inputJson = FileInputSupport.readBoundedText(
            path = Path.of(cliOptions.inputFilePath),
            kind = "input JSON"
        )
        val inputMap = readInputMap(inputJson = inputJson)

        val preparedContext = prepareRuleContext(inputMap = inputMap, schema = schema)
        val evaluationResult = engine.evaluate(prepared = preparedContext, includeTrace = cliOptions.traceEnabled)

        writeEvaluationResult(
            evaluationResult = evaluationResult,
            includeTrace = cliOptions.traceEnabled,
            format = cliOptions.format,
            out = out,
        )
        return 0
    }

    private fun parseArguments(args: Array<String>): Map<String, String?> {
        // simple args parser that supports flags (no value) and key value pairs
        val argumentsByFlag = mutableMapOf<String, String?>()
        var index = 0
        while (index < args.size) {
            val flagName = args[index]
            val flagValue = if (index + 1 < args.size && !args[index + 1].startsWith(prefix = "--")) {
                args[index + 1]
            } else {
                null
            }

            argumentsByFlag[flagName] = flagValue
            index += if (flagValue != null) 2 else 1
        }

        return argumentsByFlag
    }

    private fun readCliOptions(parsedArguments: Map<String, String?>, out: Appendable): CliOptions? {
        val schemaPath = parsedArguments["--schema"] ?: return null.also {
            usage(out = out)
        }
        val rulesPath = parsedArguments["--rules"] ?: return null.also {
            usage(out = out)
        }
        val inputFilePath = parsedArguments["--input-file"] ?: return null.also {
            usage(out = out)
        }

        return CliOptions(
            schemaPath = schemaPath,
            rulesPath = rulesPath,
            inputFilePath = inputFilePath,
            traceEnabled = parsedArguments.containsKey("--trace"),
            format = parsedArguments["--format"]?.lowercase(),
        )
    }

    private fun loadRuleAstList(rulesDirectory: Path): List<RuleAst> {
        return FileInputSupport.walkRuleFiles(root = rulesDirectory).flatMap { ruleFile ->
            Parser(
                input = FileInputSupport.readBoundedText(
                    path = ruleFile,
                    kind = "rule file"
                )
            ).parseRules()
        }
    }

    private fun readInputMap(inputJson: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return JacksonUtil.jsonMapper.readValue(inputJson, Map::class.java) as Map<String, Any?>
    }

    private fun prepareRuleContext(inputMap: Map<String, Any?>, schema: FieldSchema): PreparedRuleContext {
        val ruleContext = RuleContext.of(entries = inputMap.entries.map {
            it.key to it.value
        }.toTypedArray())

        return PreparedRuleContext.prepare(ctx = ruleContext, schema = schema)
    }

    private fun writeEvaluationResult(
        evaluationResult: EvaluationResult,
        includeTrace: Boolean,
        format: String?,
        out: Appendable,
    ) {
        val outputMap = mutableMapOf<String, Any?>()
        outputMap["matches"] = evaluationResult.matches.map { match ->
            mapOf(
                "ruleId" to match.ruleId,
                "actions" to match.actions.map { action ->
                    mapOf(
                        "name" to action.name,
                        "arguments" to action.arguments,
                    )
                },
            )
        }
        if (includeTrace && evaluationResult.trace != null) {
            // result.trace is DecisionTree
            outputMap["decisionTree"] = evaluationResult.trace
        }

        if (format == "pretty-json") {
            out.append(JacksonUtil.jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(outputMap))
        } else {
            out.append(JacksonUtil.jsonMapper.writeValueAsString(outputMap))
        }
        out.append("\n")
    }

    private fun usage(out: Appendable): Int {
        out.append("Usage: --schema <schema.yaml> ")
            .append("--rules <rules-dir> ")
            .append("--input-file <input.json> ")
            .append("[--trace] [--format json|pretty-json]\n")
        return 2
    }

    private data class CliOptions(
        val schemaPath: String,
        val rulesPath: String,
        val inputFilePath: String,
        val traceEnabled: Boolean,
        val format: String?,
    )
}

