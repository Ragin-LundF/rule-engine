package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.OrAst
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

    /**
     * Rewrites word-form operators to the symbols the Builder's dropdowns offer, so `hour lt 8` and
     * `hour < 8` compare equal. That normalisation is deliberate — see
     * [RuleAstToBuilderMapper.normalizeOperator] — and is the one difference a faithful round-trip is
     * allowed to introduce. Everything else must survive untouched.
     */
    private fun canonical(expr: ExpressionAst): ExpressionAst = when (expr) {
        is ConditionAst -> expr.copy(operator = RuleAstToBuilderMapper.normalizeOperator(expr.operator))
        is AndAst -> AndAst(children = expr.children.map { canonical(expr = it) })
        is OrAst -> OrAst(children = expr.children.map { canonical(expr = it) })
        is NotAst -> NotAst(child = canonical(expr = expr.child))
        is ComparisonExpressionAst -> expr
    }

    /**
     * Every sample, not just one: a rule that validates but locks the Builder — as
     * `warehouse-shipments`' `count(parcels[origin.hub == "HAM"])` did — is only caught by running
     * both checks over all of them.
     */
    @Test
    fun `every sample validates and is fully editable in the builder`() {
        Files.list(samplesDir).use { stream ->
            stream.filter { Files.isDirectory(it) }.sorted().toList()
        }.forEach { sample ->
            val name = sample.fileName
            val schema = FieldSchemaLoader.load(path = sample.resolve("schema.yaml"))
            val actions = ActionSchemaLoader.load(path = sample.resolve("actions.yaml"))

            val dsl = ruleFiles(sample = sample).joinToString(separator = "\n\n") { Files.readString(it) }
            val asts = Parser(input = dsl).parseRules()
            assertTrue(actual = asts.isNotEmpty(), message = "Sample '$name' should contain rules")

            val errors = Validator.validate(asts = asts, schema = schema, actions = actions)
                .diagnostics.filter { it.severity == Severity.ERROR }
            assertTrue(
                actual = errors.isEmpty(),
                message = "Sample '$name' has validation errors: ${errors.map { it.message }}",
            )

            asts.forEach { ast ->
                val builderRule = RuleAstToBuilderMapper.map(rule = ast)
                assertTrue(
                    actual = builderRule is BuilderRule.Supported,
                    message = "Rule '${ast.id}' of '$name' should be editable in Builder mode, " +
                        "but was locked: " + (builderRule as? BuilderRule.Unsupported)?.reason.orEmpty(),
                )

                // Round-trip the rule and confirm the condition still means the same thing.
                val state = BuilderEditorState.fromBuilderRule(rule = builderRule)
                val generated = assertNotNull(
                    actual = BuilderToRuleDsl.generate(state = state),
                    message = "Rule '${ast.id}' of '$name' produced no DSL",
                )
                val reparsed = Parser(input = generated).parseRules().single()
                assertEquals(
                    expected = canonical(expr = ast.condition),
                    actual = canonical(expr = reparsed.condition),
                    message = "Round-trip changed rule '${ast.id}' of '$name'.\nGenerated:\n$generated",
                )
            }
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
}
