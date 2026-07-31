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
import ruleengine.core.domain.ActionSchema
import ruleengine.core.io.FileInputSupport
import ruleengine.jackson.JacksonUtil
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * CLI to evaluate a single input JSON against a rule set.
 *
 * Two modes:
 *   Manifest mode (authoritative for execution order — manifest file order, then in-file
 *   declaration order):
 *     --manifest <manifest.yaml> [--entry <id>] --input-file <input.json> [--trace]
 *       [--format json|pretty-json]
 *   Directory mode (rule files walked in sorted path order):
 *     --schema <schema.yaml> --rules <rules-dir> --input-file <input.json> [--trace]
 *       [--format json|pretty-json]
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

        val loaded = loadRules(cliOptions = cliOptions, out = out) ?: return 2

        val validationResult = Validator.validate(
            asts = loaded.asts,
            schema = loaded.schema,
            actions = loaded.actions
        )
        if (!validationResult.isValid) {
            out.append("Validation failed: ${validationResult.diagnostics}\n")
            return 1
        }

        val compiledRules = Compiler.compileRules(
            asts = loaded.asts,
            schema = loaded.schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        val engine = RuleEngine(compiledRules = compiledRules)

        val inputJson = FileInputSupport.readBoundedText(
            path = Path.of(cliOptions.inputFilePath),
            kind = "input JSON"
        )
        val inputMap = readInputMap(inputJson = inputJson)

        val preparedContext = prepareRuleContext(inputMap = inputMap, schema = loaded.schema)
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
        val inputFilePath = parsedArguments["--input-file"] ?: return null.also {
            usage(out = out)
        }
        val manifestPath = parsedArguments["--manifest"]
        val schemaPath = parsedArguments["--schema"]
        val rulesPath = parsedArguments["--rules"]

        // Either manifest mode (--manifest) or directory mode (--schema + --rules) is required.
        val validSelection = manifestPath != null || (schemaPath != null && rulesPath != null)
        if (!validSelection) {
            usage(out = out)
            return null
        }

        return CliOptions(
            manifestPath = manifestPath,
            entryId = parsedArguments["--entry"],
            schemaPath = schemaPath,
            rulesPath = rulesPath,
            inputFilePath = inputFilePath,
            traceEnabled = parsedArguments.containsKey("--trace"),
            format = parsedArguments["--format"]?.lowercase(),
        )
    }

    /**
     * Resolves the field schema, ordered rule ASTs, and optional action schema for evaluation.
     * Manifest mode is authoritative for ordering: rules are collected in the manifest's
     * `rules` list order, then in-file declaration order. Directory mode walks the rules folder
     * in sorted (alphabetical) path order. Returns null after printing an error on failure.
     */
    private fun loadRules(cliOptions: CliOptions, out: Appendable): LoadedRules? {
        return if (cliOptions.manifestPath != null) {
            loadFromManifest(manifestPath = cliOptions.manifestPath, entryId = cliOptions.entryId, out = out)
        } else {
            loadFromDirectory(schemaPath = cliOptions.schemaPath!!, rulesPath = cliOptions.rulesPath!!, out = out)
        }
    }

    private fun loadFromDirectory(schemaPath: String, rulesPath: String, out: Appendable): LoadedRules? {
        val schema = FieldSchemaLoader.load(path = Path.of(schemaPath))
        val rulesDirectory = Path.of(rulesPath)
        if (!Files.exists(rulesDirectory) || !Files.isDirectory(rulesDirectory)) {
            out.append("Rules path is not a directory: $rulesPath\n")
            return null
        }
        val asts = FileInputSupport.walkRuleFiles(root = rulesDirectory).flatMap { ruleFile ->
            Parser(input = FileInputSupport.readBoundedText(path = ruleFile, kind = "rule file")).parseRules()
        }
        return LoadedRules(schema = schema, asts = asts, actions = null)
    }

    @Suppress("ReturnCount")
    private fun loadFromManifest(manifestPath: String, entryId: String?, out: Appendable): LoadedRules? {
        val manifestFile = Path.of(manifestPath)
        val manifest = ManifestLoader.load(path = manifestFile)
        val entry = if (entryId != null) {
            manifest.entries.firstOrNull { it.id == entryId }
        } else {
            manifest.entries.firstOrNull()
        }
        if (entry == null) {
            val detail = entryId?.let { "no entry with id '$it'" } ?: "manifest contains no entries"
            out.append("Manifest error: $detail\n")
            return null
        }
        val baseDir = manifestFile.toAbsolutePath().parent
        val schemaRel = entry.schema
        if (schemaRel == null) {
            out.append("Manifest error: entry '${entry.id}' has no schema\n")
            return null
        }
        val schema = FieldSchemaLoader.load(path = baseDir.resolve(schemaRel))
        val actions = entry.actions?.let { ActionSchemaLoader.load(path = baseDir.resolve(it)) }
        val asts = entry.rules.flatMap { rel ->
            Parser(
                input = FileInputSupport.readBoundedText(path = baseDir.resolve(rel), kind = "rule file")
            ).parseRules()
        }
        return LoadedRules(schema = schema, asts = asts, actions = actions)
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
        out.append("Usage (manifest mode, honors rule order): ")
            .append("--manifest <manifest.yaml> [--entry <id>] ")
            .append("--input-file <input.json> [--trace] [--format json|pretty-json]\n")
        out.append("Usage (directory mode): ")
            .append("--schema <schema.yaml> --rules <rules-dir> ")
            .append("--input-file <input.json> [--trace] [--format json|pretty-json]\n")
        return 2
    }

    private data class CliOptions(
        val manifestPath: String?,
        val entryId: String?,
        val schemaPath: String?,
        val rulesPath: String?,
        val inputFilePath: String,
        val traceEnabled: Boolean,
        val format: String?,
    )

    private data class LoadedRules(
        val schema: FieldSchema,
        val asts: List<RuleAst>,
        val actions: ActionSchema?,
    )
}

