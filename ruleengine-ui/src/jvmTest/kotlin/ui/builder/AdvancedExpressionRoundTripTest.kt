package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.compiled.AggregateFunctionName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The regression net for advanced expressions in Builder mode.
 *
 * Each case parses DSL, maps it to the Builder model, renders it back, and re-parses. Asserting that
 * the two ASTs are **equal** is what catches a lossy mapper: a dropped filter, a dropped `ignoreCase`,
 * or a mis-parenthesised calculation all change the AST even when the text still looks plausible.
 */
class AdvancedExpressionRoundTripTest {

    private fun field(name: String, type: FieldType, nested: List<FieldDefinition> = emptyList()) =
        FieldDefinition(
            id = FieldId(value = name),
            type = type,
            fields = nested.associateBy { it.id },
        )

    /**
     * orders: collection { status: text, total: decimal, items: collection { price: decimal, sku: text } }
     * plus scalar fields for the plain-condition cases.
     */
    private val schema = FieldSchema(
        name = "advanced-test",
        fields = listOf(
            field(
                name = "orders",
                type = FieldType.COLLECTION,
                nested = listOf(
                    field(name = "status", type = FieldType.TEXT),
                    field(name = "total", type = FieldType.DECIMAL),
                    field(
                        name = "items",
                        type = FieldType.COLLECTION,
                        nested = listOf(
                            field(name = "price", type = FieldType.DECIMAL),
                            field(name = "sku", type = FieldType.TEXT),
                        ),
                    ),
                ),
            ),
            field(name = "refunds", type = FieldType.COLLECTION),
            field(name = "amount", type = FieldType.DECIMAL),
            field(name = "purpose", type = FieldType.TEXT),
            field(name = "iban", type = FieldType.TEXT),
        ).associateBy { it.id },
    )

    private fun wrap(condition: String): String = """
        rule "advanced" {
          when
            $condition
          then
            flag "hit"
        }
    """.trimIndent()

    /**
     * Parses [condition], maps it into the Builder, renders it back out, and returns the re-parsed
     * rule together with the generated text.
     */
    private fun roundTrip(condition: String): Pair<ruleengine.dsl.ast.RuleAst, String> {
        val originalDsl = wrap(condition = condition)
        val originalAst = Parser(input = originalDsl).parseRules().single()

        val builderRule = RuleAstToBuilderMapper.map(rule = originalAst)
        assertTrue(
            actual = builderRule is BuilderRule.Supported,
            message = "Builder locked for '$condition': " +
                (builderRule as? BuilderRule.Unsupported)?.reason.orEmpty(),
        )

        val state = BuilderEditorState.fromBuilderRule(rule = builderRule)
        val generated = assertNotNull(actual = BuilderToRuleDsl.generate(state = state))
        val reparsed = Parser(input = generated).parseRules().single()
        return reparsed to generated
    }

    /** Asserts the condition survives the round-trip with an identical AST and still validates. */
    private fun assertRoundTrips(condition: String) {
        val originalAst = Parser(input = wrap(condition = condition)).parseRules().single()
        val (reparsed, generated) = roundTrip(condition = condition)

        assertEquals(
            expected = originalAst.condition,
            actual = reparsed.condition,
            message = "AST changed for '$condition'.\nGenerated:\n$generated",
        )

        val errors = Validator.validate(
            asts = listOf(reparsed),
            schema = schema,
            actions = null,
        ).diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(
            actual = errors.isEmpty(),
            message = "Generated DSL for '$condition' does not validate: ${errors.map { it.message }}",
        )
    }

    // ── aggregates ────────────────────────────────────────────────────────────

    @Test
    fun `simple aggregate round-trips`() {
        assertRoundTrips(condition = "sum(orders.total) > 1000")
    }

    @Test
    fun `every aggregate function round-trips`() {
        OperatorOptions.AGGREGATE_FUNCTIONS.forEach { function ->
            assertRoundTrips(condition = "$function(orders.total) > 1")
        }
    }

    @Test
    fun `filtered aggregate round-trips`() {
        assertRoundTrips(condition = """sum(orders[status == "paid"].total) > 500""")
    }

    @Test
    fun `deeply nested filtered path round-trips`() {
        assertRoundTrips(
            condition = """sum(orders[status == "paid"].items[price > 0].price) > 100"""
        )
    }

    @Test
    fun `count of a filtered collection round-trips`() {
        assertRoundTrips(condition = """count(orders[status == "paid"]) > 0""")
    }

    @Test
    fun `aggregate on both sides round-trips`() {
        assertRoundTrips(condition = "sum(orders.total) > sum(refunds.amount)")
    }

    // ── arithmetic ────────────────────────────────────────────────────────────

    @Test
    fun `aggregate times literal round-trips`() {
        assertRoundTrips(condition = "sum(orders.total) > count(orders) * 0.5")
    }

    @Test
    fun `parenthesised sum round-trips`() {
        assertRoundTrips(
            condition = "(sum(orders.total) + sum(refunds.amount)) * 0.5 > 10"
        )
    }

    @Test
    fun `three term chain round-trips`() {
        assertRoundTrips(condition = "amount + amount + amount > 30")
    }

    @Test
    fun `mixed precedence chain round-trips`() {
        assertRoundTrips(condition = "amount * 2 + 5 > 30")
    }

    @Test
    fun `division round-trips`() {
        assertRoundTrips(condition = "sum(orders.total) / count(orders) >= 25")
    }

    /** The documented percentage pattern from docs/expressions.md §8. */
    @Test
    fun `risk ratio example round-trips`() {
        assertRoundTrips(
            condition = """sum(orders[status == "paid"].total) > sum(orders[total > 0].total) * 0.03"""
        )
    }

    // ── not / ignoreCase ──────────────────────────────────────────────────────

    @Test
    fun `not on a plain condition round-trips`() {
        assertRoundTrips(condition = """not iban regex "^DE"""")
    }

    @Test
    fun `not on an aggregate comparison round-trips`() {
        assertRoundTrips(condition = "not sum(orders.total) > 1000")
    }

    @Test
    fun `ignoreCase on a text condition round-trips`() {
        assertRoundTrips(condition = """purpose contains "rent" ignoreCase""")
    }

    // ── combinations ──────────────────────────────────────────────────────────

    @Test
    fun `aggregate combined with plain conditions round-trips`() {
        assertRoundTrips(
            condition = """purpose contains "rent" and sum(orders.total) > 100 and amount >= 5"""
        )
    }

    @Test
    fun `or of two aggregates round-trips`() {
        assertRoundTrips(condition = "sum(orders.total) > 100 or count(orders) > 3")
    }

    @Test
    fun `text equality on a nested path round-trips`() {
        assertRoundTrips(condition = """orders.status == "paid"""")
    }

    // ── model shape ───────────────────────────────────────────────────────────

    @Test
    fun `filters are attached to the segment they filter`() {
        val ast = Parser(input = wrap(condition = """sum(orders[status == "paid"].items[price > 0].price) > 1"""))
            .parseRules().single()
        val rule = RuleAstToBuilderMapper.map(rule = ast) as BuilderRule.Supported
        val comparison = rule.conditionNodes.single() as BuilderConditionNode.Comparison
        val aggregate = comparison.left as BuilderOperand.Aggregate

        assertEquals(expected = "sum", actual = aggregate.function)
        assertEquals(expected = listOf("orders", "items", "price"), actual = aggregate.path.names)
        assertEquals(expected = 1, actual = aggregate.path[0].filters.size)
        assertEquals(expected = "status", actual = aggregate.path[0].filters.single().field)
        assertEquals(expected = 1, actual = aggregate.path[1].filters.size)
        assertEquals(expected = "price", actual = aggregate.path[1].filters.single().field)
        assertTrue(actual = aggregate.path[2].filters.isEmpty())
    }

    @Test
    fun `path pickers offer members at every depth`() {
        val catalog = schema.fields.values.map { it.toCatalogFieldInfo() }

        assertEquals(
            expected = listOf("status", "total", "items"),
            actual = OperandRules
                .segmentOptions(fields = catalog, path = listOf(BuilderPathStep(name = "orders")), depth = 1)
                .map { it.id },
        )
        assertEquals(
            expected = listOf("price", "sku"),
            actual = OperandRules.segmentOptions(
                fields = catalog,
                path = listOf(BuilderPathStep(name = "orders"), BuilderPathStep(name = "items")),
                depth = 2,
            ).map { it.id },
        )
    }

    @Test
    fun `computed operand kinds are hidden for a text field but offered for a numeric one`() {
        val catalog = schema.fields.values.map { it.toCatalogFieldInfo() }

        val forText = OperandRules.availableKinds(other = pathOperand(dotted = "purpose"), fields = catalog)
        assertEquals(
            expected = listOf(OperandRules.OperandKind.FIELD, OperandRules.OperandKind.VALUE),
            actual = forText,
        )

        val forNumeric = OperandRules.availableKinds(other = pathOperand(dotted = "amount"), fields = catalog)
        assertTrue(actual = OperandRules.OperandKind.AGGREGATE in forNumeric)
        assertTrue(actual = OperandRules.OperandKind.CALCULATION in forNumeric)
    }

    @Test
    fun `text comparison offers equality operators only`() {
        val catalog = schema.fields.values.map { it.toCatalogFieldInfo() }
        val operators = OperandRules.operatorsFor(
            left = pathOperand(dotted = "purpose"),
            right = BuilderOperand.Literal(text = "rent", numeric = false),
            fields = catalog,
        )
        assertEquals(expected = OperatorOptions.COMPARISON_TEXT, actual = operators)
    }

    // ── locking ───────────────────────────────────────────────────────────────

    @Test
    fun `extraction rules stay locked with a message naming extractions`() {
        val dsl = """
            rule "extract-iban" {
              when
                purpose contains "rent"
              then
                extract iban regex("DE(\d+)", 1) tag $1
            }
        """.trimIndent()
        val ast = Parser(input = dsl).parseRules().single()
        val rule = RuleAstToBuilderMapper.map(rule = ast)

        val unsupported = rule as? BuilderRule.Unsupported
        assertNotNull(actual = unsupported, message = "Extraction rules must stay locked")
        assertTrue(
            actual = unsupported.reason.contains(other = "extraction", ignoreCase = true),
            message = "Lock reason should name extractions, got: ${unsupported.reason}",
        )
    }

    // ── drift guard ───────────────────────────────────────────────────────────

    @Test
    fun `builder aggregate list matches the engine`() {
        assertEquals(
            expected = AggregateFunctionName.entries.map { it.name.lowercase() }.sorted(),
            actual = OperatorOptions.AGGREGATE_FUNCTIONS.sorted(),
            message = "OperatorOptions.AGGREGATE_FUNCTIONS drifted from AggregateFunctionName",
        )
    }
}
