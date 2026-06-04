package ruleengine.cli

import ruleengine.schema.FieldSchemaLoader
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Validator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import ruleengine.core.domain.DefaultActionSchema
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Simple CLI validator.
 * Usage: --schema <schema.yaml> --rules <rules-dir>
 */
object ValidatorCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val exit = runCli(args)
        kotlin.system.exitProcess(exit)
    }

    fun runCli(args: Array<String>, out: Appendable = System.out): Int {
        try {
            val map = args.asList().windowed(2, 2).associate { it[0] to it[1] }
            val schemaPath = map["--schema"] ?: return usage()
            val rulesPath = map["--rules"] ?: return usage()
            val actionsPath = map["--actions"]
            val format = map["--format"]?.lowercase()

            val schema = FieldSchemaLoader.load(Path.of(schemaPath))

            val rulesDir = Path.of(rulesPath)
            if (!Files.exists(rulesDir) || !Files.isDirectory(rulesDir)) {
                out.append("Rules path is not a directory: $rulesPath\n")
                return 2
            }

            val ruleFiles = Files.walk(rulesDir).filter { it.isRegularFile() && it.toString().endsWith(".rule") }.toList()
            if (ruleFiles.isEmpty()) {
                out.append("No .rule files found in $rulesPath\n")
                return 0
            }

            var hadErrors = false
            val asts = mutableListOf<ruleengine.dsl.ast.RuleAst>()
            for (f in ruleFiles) {
                val txt = Files.readString(f)
                try {
                    val parsed = Parser(txt).parseRules()
                    asts += parsed
                } catch (ex: Exception) {
                    hadErrors = true
                    out.append("Failed to parse ${f}: ${ex.message}\n")
                }
            }

            val actionSchema = if (actionsPath != null) ruleengine.schema.ActionSchemaLoader.load(Path.of(actionsPath)) else DefaultActionSchema.basic
            val validation = Validator.validate(asts = asts, schema = schema, actions = actionSchema)

            // determine if there are errors
            hadErrors = validation.diagnostics.any { it.severity == ruleengine.core.errors.Severity.ERROR }
            val exitCode = if (hadErrors) 1 else 0

            if (format == "json" || format == "pretty-json") {
                val mapper = com.fasterxml.jackson.databind.ObjectMapper().registerKotlinModule()
                val diagnostics = validation.diagnostics.map { d ->
                    mapOf(
                        "severity" to d.severity.name,
                        "message" to d.message,
                        "suggestion" to d.suggestion,
                        "file" to (d.file?.toString()),
                        "line" to d.line,
                        "column" to d.column
                    )
                }
                val outMap = mapOf("diagnostics" to diagnostics, "ok" to !hadErrors, "exitCode" to exitCode)
                if (format == "pretty-json") out.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(outMap)) else out.append(mapper.writeValueAsString(outMap))
            } else {
                for (d in validation.diagnostics) {
                    val loc = if (d.file != null) " (${d.file}:${d.line ?: "?"}:${d.column ?: "?"})" else ""
                    val suggestion = if (d.suggestion != null) " Did you mean: ${d.suggestion}?" else ""
                    out.append("${d.severity}: ${d.message}$suggestion$loc\n")
                }
            }

            return if (hadErrors) 1 else 0
        } catch (ex: Exception) {
            out.append("Error: ${ex.message}\n")
            return 3
        }
    }

    private fun usage(): Int {
        println("Usage: --schema <schema.yaml> --rules <rules-dir>")
        return 2
    }
}


