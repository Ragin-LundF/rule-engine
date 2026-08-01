package ruleengine.cli

import ruleengine.builder.LoadedRuleEngine
import ruleengine.builder.RuleEngineBuilder
import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.core.errors.Severity
import ruleengine.core.io.FileInputSupport
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.jackson.JacksonUtil
import ruleengine.manifest.ManifestLoader
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
@Suppress("TooManyFunctions")
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
        val parsedArguments = CliArguments.parse(args = args)
        val cliOptions = readCliOptions(parsedArguments = parsedArguments, out = out) ?: return 2

        val outcome = loadEngine(cliOptions = cliOptions, out = out)
        val loaded = outcome.engine ?: return outcome.exitCode

        val inputJson = FileInputSupport.readBoundedText(
            path = Path.of(cliOptions.inputFilePath),
            kind = "input JSON"
        )
        val inputMap = readInputMap(inputJson = inputJson)

        val evaluationResult = loaded.evaluate(input = inputMap, includeTrace = cliOptions.traceEnabled)

        writeEvaluationResult(
            evaluationResult = evaluationResult,
            includeTrace = cliOptions.traceEnabled,
            format = cliOptions.format,
            out = out,
        )
        return 0
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
     * Builds the engine for evaluation.
     * Manifest mode delegates to [RuleEngineBuilder] and is authoritative for ordering: rules are
     * collected in the manifest's `rules` list order, then in-file declaration order. Directory mode
     * walks the rules folder in sorted (alphabetical) path order. On failure the outcome carries a
     * null engine plus the exit code, after the reason has been printed.
     */
    private fun loadEngine(cliOptions: CliOptions, out: Appendable): LoadOutcome {
        return if (cliOptions.manifestPath != null) {
            loadFromManifest(manifestPath = cliOptions.manifestPath, entryId = cliOptions.entryId, out = out)
        } else {
            loadFromDirectory(schemaPath = cliOptions.schemaPath!!, rulesPath = cliOptions.rulesPath!!, out = out)
        }
    }

    private fun loadFromDirectory(schemaPath: String, rulesPath: String, out: Appendable): LoadOutcome {
        val schema = FieldSchemaLoader.load(path = Path.of(schemaPath))
        val rulesDirectory = Path.of(rulesPath)
        if (!Files.exists(rulesDirectory) || !Files.isDirectory(rulesDirectory)) {
            out.append("Rules path is not a directory: $rulesPath\n")
            return LoadOutcome(engine = null, exitCode = 2)
        }
        val asts = FileInputSupport.walkRuleFiles(root = rulesDirectory).flatMap { ruleFile ->
            Parser(input = FileInputSupport.readBoundedText(path = ruleFile, kind = "rule file")).parseRules()
        }

        val validationResult = Validator.validate(asts = asts, schema = schema, actions = null)
        if (!validationResult.isValid) {
            out.append("Validation failed: ${validationResult.diagnostics}\n")
            return LoadOutcome(engine = null, exitCode = 1)
        }

        val compiledRules = Compiler.compileRules(
            asts = asts,
            schema = schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        return LoadOutcome(
            engine = LoadedRuleEngine(
                entryId = rulesPath,
                engine = RuleEngine(compiledRules = compiledRules),
                schema = schema,
                warnings = validationResult.diagnostics,
            )
        )
    }

    private fun loadFromManifest(manifestPath: String, entryId: String?, out: Appendable): LoadOutcome {
        val manifestFile = Path.of(manifestPath)

        return runCatching {
            // Without --entry the first manifest entry is used, so sibling entries are never loaded.
            val resolvedEntryId = entryId
                ?: ManifestLoader.load(path = manifestFile).entries.firstOrNull()?.id
                ?: error("manifest contains no entries")

            LoadOutcome(
                engine = RuleEngineBuilder.fromManifestEntry(manifestPath = manifestFile, entryId = resolvedEntryId)
            )
        }.getOrElse { failure ->
            out.append("Manifest error: ${failure.message}\n")
            LoadOutcome(engine = null, exitCode = if (failure.isValidationFailure()) 1 else 2)
        }
    }

    private fun Throwable.isValidationFailure(): Boolean =
        this is RuleEngineBuildException && diagnostics.any { it.severity == Severity.ERROR }

    private fun readInputMap(inputJson: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return JacksonUtil.jsonMapper.readValue(inputJson, Map::class.java) as Map<String, Any?>
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

    /** Either a ready engine, or a null engine plus the exit code to return. */
    private data class LoadOutcome(
        val engine: LoadedRuleEngine?,
        val exitCode: Int = 0,
    )
}

