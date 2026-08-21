package ruleengine.cli

import ruleengine.builder.EntryInputs
import ruleengine.builder.RuleEngineBuilder
import ruleengine.compiler.EntryValidator
import ruleengine.compiler.RuleFileAsts
import ruleengine.compiler.ValidationResult
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.core.io.FileInputSupport
import ruleengine.dsl.parser.Parser
import ruleengine.jackson.JacksonUtil
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * CLI to validate rules.
 *
 * Two modes, mirroring [EvaluateCli]:
 *
 *   Manifest mode — validates a whole entry the way the engine loads it, so action names, argument
 *   types and variables that cross rule files are all checked, and every diagnostic names the file it
 *   came from:
 *     --manifest <manifest.yaml> [--entry <id>] [--format json|pretty-json]
 *
 *   Directory mode — a schema and a folder of rule files, walked in sorted path order. Pass `--actions`
 *   to have action names and argument types checked here too; without it they are not:
 *     --schema <schema.yaml> --rules <rules-dir> [--actions <actions.yaml>] [--format json|pretty-json]
 *
 * Exit codes: 0 valid, 1 invalid, 2 usage or a path that is not usable, 3 anything thrown.
 */
object ValidatorCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val exit = runCli(args = args)
        exitProcess(status = exit)
    }

    fun runCli(args: Array<String>, out: Appendable = System.out): Int {
        return runCatching {
            executeCli(args = args, out = out)
        }.getOrElse { cause ->
            out.append("Error: ${cause.message}\n")
            EXIT_THREW
        }
    }

    private fun executeCli(args: Array<String>, out: Appendable): Int {
        val argsMap = CliArguments.parse(args = args)
        val format = argsMap["--format"]?.lowercase()

        val manifestPath = argsMap["--manifest"]
        if (manifestPath != null) {
            return report(
                validation = validateManifest(manifestPath = manifestPath, entryId = argsMap["--entry"]),
                format = format,
                out = out,
            )
        }

        val schemaPath = argsMap["--schema"]
        val rulesPath = argsMap["--rules"]
        if (schemaPath == null || rulesPath == null) {
            return usage(out = out)
        }

        val validation = validateDirectory(
            schemaPath = schemaPath,
            rulesPath = rulesPath,
            actionsPath = argsMap["--actions"],
            out = out,
        ) ?: return EXIT_UNUSABLE

        return report(validation = validation, format = format, out = out)
    }

    /**
     * Manifest mode: the entry as the engine would load it.
     *
     * Worth preferring over directory mode for anything generated, because it is the only mode that
     * covers what a manifest adds — the action schema, the file order that decides which variables are
     * in scope where, and rule ids repeated across files.
     */
    private fun validateManifest(manifestPath: String, entryId: String?): ValidationResult {
        val inputs: EntryInputs = RuleEngineBuilder.loadEntryInputs(location = manifestPath, entryId = entryId)
        return EntryValidator.validate(
            files = inputs.files,
            schema = inputs.schema,
            actions = inputs.actions,
        )
    }

    /** Directory mode. Returns null once it has reported a path it cannot use. */
    private fun validateDirectory(
        schemaPath: String,
        rulesPath: String,
        actionsPath: String?,
        out: Appendable,
    ): ValidationResult? {
        val rulesDir = Path.of(rulesPath)
        if (!Files.exists(rulesDir) || !Files.isDirectory(rulesDir)) {
            out.append("Rules path is not a directory: $rulesPath\n")
            return null
        }

        val schema = FieldSchemaLoader.load(path = Path.of(schemaPath))
        val actions = actionsPath?.let { path -> ActionSchemaLoader.load(path = Path.of(path)) }
        val files = FileInputSupport.walkRuleFiles(root = rulesDir).map { file ->
            RuleFileAsts(
                path = file.toString(),
                asts = Parser(input = FileInputSupport.readBoundedText(path = file, kind = "rule file")).parseRules(),
            )
        }

        // Through the entry validator even here, so a diagnostic names its file in both modes. Walk
        // order stands in for manifest order, which is what this mode has always used.
        return EntryValidator.validate(files = files, schema = schema, actions = actions)
    }

    private fun report(validation: ValidationResult, format: String?, out: Appendable): Int {
        val exitCode = if (validation.isValid) EXIT_VALID else EXIT_INVALID
        if (format == "json") {
            writeJson(validation = validation, exitCode = exitCode, out = out)
            return exitCode
        }

        if (!validation.isValid) {
            out.append("Validation failed:\n")
            // One per line, and the position first: the point of this output is that someone — or
            // something generating rules — can read it and go straight to the file. A single line of
            // `ValidationDiagnostic(...)` toString for a whole entry cannot be read at all.
            validation.diagnostics.forEach { diagnostic -> out.append("  ${describe(diagnostic = diagnostic)}\n") }
            return exitCode
        }
        out.append("Validation OK\n")
        // Warnings never fail the run, so they are the one thing that would otherwise be silent in a
        // successful one — and "a variable two rules assign" is worth reading before shipping.
        validation.diagnostics.forEach { diagnostic -> out.append("  ${describe(diagnostic = diagnostic)}\n") }
        return exitCode
    }

    /** One diagnostic as a line: `[SEVERITY] file:line:column message → suggestion`. */
    private fun describe(diagnostic: ValidationDiagnostic): String {
        val position = listOfNotNull(
            diagnostic.file?.toString(),
            diagnostic.line?.toString(),
            diagnostic.column?.toString(),
        ).joinToString(separator = ":")
        val where = if (position.isEmpty()) "" else "$position "
        val fix = diagnostic.suggestion?.let { suggestion -> " → $suggestion" }.orEmpty()
        return "[${diagnostic.severity}] $where${diagnostic.message}$fix"
    }

    private fun writeJson(validation: ValidationResult, exitCode: Int, out: Appendable) {
        val payload = mutableMapOf<String, Any?>()
        payload["diagnostics"] = validation.diagnostics
        payload["ok"] = validation.isValid
        payload["exitCode"] = exitCode

        val mapper = JacksonUtil.jsonMapper
        // Pretty only when there is something to read: a clean run is a one-line "ok", a failing one is
        // a list someone has to work through.
        val json = if (validation.isValid) {
            mapper.writeValueAsString(payload)
        } else {
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
        }
        out.append(json)
        out.append("\n")
    }

    private fun usage(out: Appendable): Int {
        out.append(
            "Usage: --manifest <manifest.yaml> [--entry <id>] [--format json|pretty-json]\n" +
                "   or: --schema <schema.yaml> --rules <rules-dir> [--actions <actions.yaml>] " +
                "[--format json|pretty-json]\n"
        )
        return EXIT_USAGE
    }

    private const val EXIT_VALID = 0
    private const val EXIT_INVALID = 1
    private const val EXIT_USAGE = 2
    private const val EXIT_UNUSABLE = 2
    private const val EXIT_THREW = 3
}
