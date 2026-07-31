package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the bundled sample projects: every rule they ship must validate, and every rule must be
 * editable in Builder mode without changing meaning.
 *
 * This is what stops the samples from rotting — a sample rule that locks the Builder or fails
 * validation would otherwise only be noticed by opening the app.
 */
class SampleProjectBuilderTest {

    private val samplesDir: Path = Path.of("src/commonMain/composeResources/files/samples")

    private fun sample(name: String): Path = samplesDir.resolve(name)

    private fun ruleFiles(sample: Path): List<Path> =
        Files.list(sample.resolve("rules")).use { stream ->
            stream.filter { it.toString().endsWith(suffix = ".rule") }.sorted().toList()
        }

    @Test
    fun `financial transactions sample validates and is fully editable in the builder`() {
        val sample = sample(name = "financial-transactions")
        val schema = FieldSchemaLoader.load(path = sample.resolve("schema.yaml"))
        val actions = ActionSchemaLoader.load(path = sample.resolve("actions.yaml"))

        val dsl = ruleFiles(sample = sample).joinToString(separator = "\n\n") { Files.readString(it) }
        val asts = Parser(input = dsl).parseRules()
        assertTrue(actual = asts.isNotEmpty(), message = "Sample should contain rules")

        val errors = Validator.validate(asts = asts, schema = schema, actions = actions)
            .diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(
            actual = errors.isEmpty(),
            message = "Sample rules must validate, got: ${errors.map { it.message }}",
        )

        asts.forEach { ast ->
            val builderRule = RuleAstToBuilderMapper.map(rule = ast)
            assertTrue(
                actual = builderRule is BuilderRule.Supported,
                message = "Rule '${ast.id}' should be editable in Builder mode, but was locked: " +
                    (builderRule as? BuilderRule.Unsupported)?.reason.orEmpty(),
            )

            // Round-trip the rule and confirm the condition still means the same thing.
            val state = BuilderEditorState.fromBuilderRule(rule = builderRule)
            val generated = assertNotNull(
                actual = BuilderToRuleDsl.generate(state = state),
                message = "Rule '${ast.id}' produced no DSL",
            )
            val reparsed = Parser(input = generated).parseRules().single()
            assertEquals(
                expected = ast.condition,
                actual = reparsed.condition,
                message = "Round-trip changed rule '${ast.id}'.\nGenerated:\n$generated",
            )
        }
    }

    @Test
    fun `sample schema declares nested collection members`() {
        val schema = FieldSchemaLoader.load(
            path = sample(name = "financial-transactions").resolve("schema.yaml")
        )
        val transactions = schema.fields.values.first { it.id.value == "transactions" }
        val catalog = transactions.toCatalogFieldInfo()

        assertEquals(expected = "collection", actual = catalog.type)
        assertTrue(
            actual = catalog.nestedFields.map { it.id }.containsAll(elements = listOf("amount", "label")),
            message = "Expected declared members, got: ${catalog.nestedFields.map { it.id }}",
        )

        // Nesting continues one level deeper, which is what the path picker walks.
        val counterparty = catalog.nestedFields.first { it.id == "counterparty" }
        assertEquals(expected = "object", actual = counterparty.type)
        assertEquals(
            expected = listOf("iban", "country"),
            actual = counterparty.nestedFields.map { it.id },
        )
    }

    @Test
    fun `all samples validate`() {
        Files.list(samplesDir).use { stream ->
            stream.filter { Files.isDirectory(it) }.sorted().toList()
        }.forEach { sample ->
            val schema = FieldSchemaLoader.load(path = sample.resolve("schema.yaml"))
            val actions = ActionSchemaLoader.load(path = sample.resolve("actions.yaml"))
            val dsl = ruleFiles(sample = sample).joinToString(separator = "\n\n") { Files.readString(it) }

            val errors = Validator.validate(
                asts = Parser(input = dsl).parseRules(),
                schema = schema,
                actions = actions,
            ).diagnostics.filter { it.severity == Severity.ERROR }

            assertTrue(
                actual = errors.isEmpty(),
                message = "Sample '${sample.fileName}' has validation errors: ${errors.map { it.message }}",
            )
        }
    }
}
