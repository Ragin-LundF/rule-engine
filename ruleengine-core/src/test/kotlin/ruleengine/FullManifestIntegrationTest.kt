package ruleengine

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.jackson.JacksonUtil
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullManifestIntegrationTest {
    @Test
    fun testManifestEvaluationAgainstSampleInputs() {
        // load the manifest
        val manifestPath = Path.of("src/test/resources/full-manifest.yaml")
        val manifest = ManifestLoader.load(path = manifestPath)

        val entry = manifest.entries.first()
        val baseDir = manifestPath.parent

        val schema = FieldSchemaLoader.load(path = baseDir.resolve(entry.schema!!))
        val actionsSchema = ActionSchemaLoader.load(path = baseDir.resolve(entry.actions!!))

        val ruleAsts = entry.rules.flatMap { ruleRel ->
            val rulePath = baseDir.resolve(ruleRel)
            Parser(input = Files.readString(rulePath)).parseRules()
        }

        val validationResult = Validator.validate(asts = ruleAsts, schema = schema, actions = actionsSchema)
        val diagnosticsSummary = validationResult.diagnostics.toString()
        assertTrue(actual = validationResult.isValid, message = "Validation failed: $diagnosticsSummary")

        val compiledRules = Compiler.compileRules(asts = ruleAsts, schema = schema)
        val engine = RuleEngine(compiledRules = compiledRules, schema = schema)

        fun evaluateInputFile(relativeInputPath: String): List<String> {
            val inputPath = baseDir.resolve(relativeInputPath)
            val inputJson = Files.readString(inputPath)

            val inputMap = runCatching {
                JacksonUtil.jsonMapper.readValue(inputJson, Map::class.java) as Map<String, Any?>
            }.getOrElse { throwable ->
                throw AssertionError("Failed to parse input JSON '${inputPath}': ${throwable.message}")
            }

            val pairsArray = inputMap.entries.map { it.key to it.value }.toTypedArray()
            val ruleContext = RuleContext.of(entries = pairsArray)
            val prepared = PreparedRuleContext.prepare(ctx = ruleContext, schema = schema)
            val result = engine.evaluate(prepared = prepared, includeTrace = false)
            return result.matches.flatMap { it.actions.map { a -> a.name } }
        }

        val rentActions = evaluateInputFile(relativeInputPath = "inputs/rent-input.json")
        println("rent actions: $rentActions")
        assertTrue(
            actual = rentActions.contains("label"),
            message = "Expected rent rule to emit 'label' action; got: $rentActions"
        )

        val vipActions = evaluateInputFile(relativeInputPath = "inputs/vip-input.json")
        println("vip actions: $vipActions")
        assertTrue(
            actual = vipActions.contains(element = "label"),
            message = "Expected vip rule to emit 'label' action; got: $vipActions"
        )
        assertTrue(
            actual = vipActions.contains(element = "score"),
            message = "Expected vip rule to emit 'score' action; got: $vipActions"
        )

        val fraudActions = evaluateInputFile(relativeInputPath = "inputs/fraud-input.json")
        println("fraud actions: $fraudActions")
        assertTrue(
            actual = fraudActions.contains(element = "flag"),
            message = "Expected fraud rule to emit 'flag' action; got: $fraudActions"
        )
        assertTrue(
            actual = fraudActions.contains(element = "score"),
            message = "Expected fraud rule to emit 'score' action; got: $fraudActions"
        )

        // Sanity check: total number of compiled rules equals rules referenced
        assertEquals(expected = entry.rules.size, actual = compiledRules.size)
    }
}



