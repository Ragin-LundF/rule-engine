package ruleengine.cli

import ruleengine.schema.FieldSchemaLoader
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Validator
import ruleengine.jackson.JacksonUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * CLI to validate rules against a schema.
 * Usage:
 *   --schema <schema.yaml> --rules <rules-dir> [--format json|pretty-json]
 */
object ValidatorCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val exit = runCli(args)
        kotlin.system.exitProcess(exit)
    }

    fun runCli(args: Array<String>, out: Appendable = System.out): Int {
        try {
            val kv = mutableMapOf<String, String?>()
            var i = 0
            while (i < args.size) {
                val k = args[i]
                val v = if (i + 1 < args.size && !args[i + 1].startsWith("--")) { args[i + 1]; } else null
                kv[k] = v
                i += if (v != null) 2 else 1
            }

            val schemaPath = kv["--schema"] ?: return usage(out)
            val rulesPath = kv["--rules"] ?: return usage(out)
            val format = kv["--format"]?.lowercase()

            val schema = FieldSchemaLoader.load(Path.of(schemaPath))

            val rulesDir = Path.of(rulesPath)
            if (!Files.exists(rulesDir) || !Files.isDirectory(rulesDir)) {
                out.append("Rules path is not a directory: $rulesPath\n")
                return 2
            }

            val ruleFiles = Files.walk(rulesDir).filter { Files.isRegularFile(it) && it.toString().endsWith(".rule") }.toList()
            val asts = ruleFiles.flatMap { f -> Parser(Files.readString(f)).parseRules() }

            val validation = Validator.validate(asts = asts, schema = schema)

            val mapper = JacksonUtil.jsonMapper
            if (format == "json") {
                val m = mutableMapOf<String, Any?>()
                m["diagnostics"] = validation.diagnostics
                m["ok"] = validation.isValid
                m["exitCode"] = if (validation.isValid) 0 else 1
                if (validation.isValid) out.append(mapper.writeValueAsString(m)) else out.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(m))
                out.append("\n")
            } else {
                if (!validation.isValid) {
                    out.append("Validation failed: ${validation.diagnostics}\n")
                    return 1
                }
                out.append("Validation OK\n")
            }

            return if (validation.isValid) 0 else 1
        } catch (ex: Exception) {
            out.append("Error: ${ex.message}\n")
            return 3
        }
    }

    private fun usage(out: Appendable): Int {
        out.append("Usage: --schema <schema.yaml> --rules <rules-dir> [--format json|pretty-json]\n")
        return 2
    }
}

