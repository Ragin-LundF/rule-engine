package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.VariableAssignmentAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.ScopedEvaluation
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
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

    /**
     * The sample's rule files in manifest order, which is the order the engine evaluates them in.
     *
     * Alphabetical order would do for rules that are independent of each other, but a rule reading a
     * variable an earlier rule publishes is only valid in the declared order — so validating a
     * differently-ordered concatenation would report forward references the shipped sample does not
     * have.
     */
    private fun ruleFiles(sample: Path): List<Path> {
        val manifest = ManifestLoader.load(path = sample.resolve("manifest.yaml"))

        return manifest.entries.flatMap { entry -> entry.rules }.map { relativePath -> sample.resolve(relativePath) }
    }

    /**
     * The schema a sample's rules are written against.
     *
     * A scoped entry's rules name the member's fields — `balance`, not `accounts.balance` — so
     * validating them against the document schema would report every one of those as unknown. The
     * engine derives the same member schema when it builds the entry.
     */
    private fun ruleSchema(sample: Path): FieldSchema {
        val schema = FieldSchemaLoader.load(path = sample.resolve("schema.yaml"))
        val scope = ManifestLoader.load(path = sample.resolve("manifest.yaml"))
            .entries.firstNotNullOfOrNull { entry -> entry.scope }
            ?: return schema
        return ScopedEvaluation.memberSchema(schema = schema, scope = scope) ?: schema
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
            val schema = ruleSchema(sample = sample)
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
                // The generated text replaces the rule in the editor, so anything the Builder does
                // not carry is deleted from the file — not merely absent from the Builder view.
                assertEquals(
                    expected = ast.description,
                    actual = reparsed.description,
                    message = "Round-trip dropped the description of rule '${ast.id}' of '$name'.",
                )
                assertEquals(
                    expected = outputShape(rule = ast),
                    actual = outputShape(rule = reparsed),
                    message = "Round-trip changed the output side of rule '${ast.id}' of '$name'.\n" +
                        "Generated:\n$generated",
                )
            }
        }
    }

    /**
     * Everything the rule produces, as one comparable value: both branches' actions and assignments, and
     * whether either branch ends the run.
     *
     * Checked because the Builder replaces the whole rule text. A mapper that quietly dropped an `else`
     * block or a `stop` would delete it from the sample on the first edit, and the condition assertion
     * above would not notice — the condition is untouched either way.
     *
     * An assignment contributes its kind as well as its name. `set topics = "x"` and `add "x" to topics`
     * publish the same name and would fingerprint identically without it, so a mapper that lost the kind
     * would turn an accumulator into a scalar unnoticed.
     */
    private fun outputShape(rule: RuleAst): String {
        return listOf(
            "then=" + rule.actions.joinToString { action -> action.name },
            "sets=" + rule.assignments.joinToString { assignment -> assignmentShape(assignment = assignment) },
            "else=" + rule.elseActions.joinToString { action -> action.name },
            "elseSets=" + rule.elseAssignments.joinToString { assignment -> assignmentShape(assignment = assignment) },
            "stopOnThen=${rule.stopOnThen}",
            "stopOnElse=${rule.stopOnElse}",
        ).joinToString(separator = " | ")
    }

    private fun assignmentShape(assignment: VariableAssignmentAst): String =
        "${assignment.kind}:${assignment.name}"

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
