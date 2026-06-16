package ruleengine.cli

import ruleengine.compiler.Validator
import ruleengine.core.io.FileInputSupport
import ruleengine.dsl.parser.Parser
import ruleengine.jackson.JacksonUtil
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * CLI to validate rules against a schema.
 * Usage:
 *   --schema <schema.yaml> --rules <rules-dir> [--format json|pretty-json]
 */
object ValidatorCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val exit = runCli(args = args)
        exitProcess(status = exit)
    }

    @Suppress("MagicNumber")
    fun runCli(args: Array<String>, out: Appendable = System.out): Int {
        var exitCode = 0

        runCatching {
            val argsMap = parseArgs(argsArray = args)

            val schemaPath = argsMap["--schema"]
            val rulesPath = argsMap["--rules"]
            if (schemaPath == null || rulesPath == null) {
                exitCode = usage(out = out)
                return@runCatching
            }

            val format = argsMap["--format"]?.lowercase()

            val schema = FieldSchemaLoader.load(path = Path.of(schemaPath))

            val rulesDir = Path.of(rulesPath)
            if (!Files.exists(rulesDir) || !Files.isDirectory(rulesDir)) {
                out.append("Rules path is not a directory: $rulesPath\n")
                exitCode = 2
                return@runCatching
            }

            val asts = FileInputSupport.walkRuleFiles(root = rulesDir).flatMap { file ->
                Parser(
                    input = FileInputSupport.readBoundedText(
                        path = file,
                        kind = "rule file"
                    )
                ).parseRules()
            }

            val validation = Validator.validate(asts = asts, schema = schema)

            val mapper = JacksonUtil.jsonMapper
            if (format == "json") {
                val m = mutableMapOf<String, Any?>()
                m["diagnostics"] = validation.diagnostics
                m["ok"] = validation.isValid
                m["exitCode"] = if (validation.isValid) 0 else 1
                if (validation.isValid) {
                    out.append(mapper.writeValueAsString(m))
                } else {
                    out.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(m))
                }
                out.append("\n")
            } else {
                if (!validation.isValid) {
                    out.append("Validation failed: ${validation.diagnostics}\n")
                    exitCode = 1
                    return@runCatching
                }
                out.append("Validation OK\n")
            }

            exitCode = if (validation.isValid) 0 else 1
        }.onFailure { ex ->
            out.append("Error: ${ex.message}\n")
            exitCode = 3
        }

        return exitCode
    }

    private fun parseArgs(argsArray: Array<String>): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        var index = 0
        while (index < argsArray.size) {
            val key = argsArray[index]
            val value = if (index + 1 < argsArray.size && !argsArray[index + 1].startsWith("--")) {
                argsArray[index + 1]
            } else {
                null
            }

            result[key] = value
            index += if (value != null) 2 else 1
        }

        return result
    }

    private fun usage(out: Appendable): Int {
        out.append("Usage: --schema <schema.yaml> --rules <rules-dir> [--format json|pretty-json]\n")
        return 2
    }
}

