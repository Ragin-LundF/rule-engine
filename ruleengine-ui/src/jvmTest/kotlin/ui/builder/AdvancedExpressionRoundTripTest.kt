package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.DslFunctions
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.BuilderRule
import ui.builder.model.fieldOperand
import ui.builder.model.filters
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.names
import ui.builder.model.pathOperand
import ui.builder.model.sort
import ui.builder.model.withFilters
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
     * orders: collection { status: text, total: decimal, origin: object { hub: text },
     *                      items: collection { price: decimal, sku: text } }
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
                        name = "origin",
                        type = FieldType.OBJECT,
                        nested = listOf(field(name = "hub", type = FieldType.TEXT)),
                    ),
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
            field(
                name = "refunds",
                type = FieldType.COLLECTION,
                nested = listOf(
                    field(name = "month", type = FieldType.TEXT),
                    field(name = "amount", type = FieldType.DECIMAL),
                ),
            ),
            field(
                name = "sales",
                type = FieldType.COLLECTION,
                nested = listOf(
                    field(name = "month", type = FieldType.TEXT),
                    field(name = "amount", type = FieldType.DECIMAL),
                ),
            ),
            field(name = "allowedStatuses", type = FieldType.STRING_SET),
            field(name = "registeredAt", type = FieldType.DATE),
            field(name = "reviewDate", type = FieldType.DATE),
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

    /**
     * A filter may compare a field the element reaches through a nested object. The engine evaluates
     * `origin.hub` against the element context, so the Builder has to keep the dotted path rather
     * than treat the filter as unrepresentable.
     */
    @Test
    fun `filter on a dotted path into the element round-trips`() {
        assertRoundTrips(condition = """count(orders[origin.hub == "HAM"]) >= 2""")
    }

    @Test
    fun `aggregate on both sides round-trips`() {
        assertRoundTrips(condition = "sum(orders.total) > sum(refunds.amount)")
    }

    // ── rich filter predicates ────────────────────────────────────────────────
    //
    // A filter holds two operands, so its sides may be whatever a comparison row's sides may be.
    // Each of these locked the Builder while the left side was a plain field string — and locked it
    // with the wrong reason, naming a function argument rather than the filter.

    @Test
    fun `filter with an aggregate on the left round-trips`() {
        assertRoundTrips(condition = "count(orders[count(items) > 2]) > 0")
    }

    @Test
    fun `filter with a filtered path on the left round-trips`() {
        assertRoundTrips(condition = """count(orders[items[price > 0].sku == "x"]) > 0""")
    }

    @Test
    fun `filter with arithmetic on the left round-trips`() {
        assertRoundTrips(condition = "count(orders[total * 2 > 100]) > 0")
    }

    /** The symmetric case: making both sides operands is what buys the right-hand one too. */
    @Test
    fun `filter with an aggregate on the right round-trips`() {
        assertRoundTrips(condition = "count(orders[total > sum(items.price)]) > 0")
    }

    /**
     * A written-out list inside a filter. The parser only ever produces one as a legacy
     * `ConditionAst`, which is the shape a filter predicate takes for `in` — so this is the case that
     * needs a list *operand* rather than a literal to survive at all.
     */
    @Test
    fun `filter with a written-out list round-trips`() {
        assertRoundTrips(condition = """count(orders[status in ["paid", "sent"]]) > 0""")
    }

    @Test
    fun `filter with a written-out list keeps its items quoted`() {
        val (_, generated) = roundTrip(condition = """count(orders[status in ["paid", "sent"]]) > 0""")

        assertTrue(
            actual = generated.contains(other = """status in ["paid", "sent"]"""),
            message = "the list must keep its brackets and quotes: $generated",
        )
    }

    /**
     * An undeclared root is legal on a multi-segment path — the engine warns rather than failing — so
     * the Builder must carry such a path through untouched instead of rewriting it against the schema.
     */
    @Test
    fun `filtered path over an undeclared root round-trips`() {
        assertRoundTrips(
            condition = """count(reports.income.accountData[accountType == "CHECKING"]) > 0"""
        )
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
        assertEquals(expected = fieldOperand(name = "status"), actual = aggregate.path[0].filters.single().left)
        assertEquals(expected = 1, actual = aggregate.path[1].filters.size)
        assertEquals(expected = fieldOperand(name = "price"), actual = aggregate.path[1].filters.single().left)
        assertTrue(actual = aggregate.path[2].filters.isEmpty())
    }

    @Test
    fun `path pickers offer members at every depth`() {
        val catalog = schema.fields.values.map { it.toCatalogFieldInfo() }

        assertEquals(
            expected = listOf("status", "total", "origin", "items"),
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

    // ── extractions ───────────────────────────────────────────────────────────

    /**
     * An extraction used to lock the rule, because `BuilderAction` had no slot for a regex and
     * regenerating the DSL without one would have deleted it from the file. It now round-trips.
     *
     * Note what the AST carries: `Lexer.readString` treats a backslash as a generic escape, so the
     * `\d` written here reaches the parser as a plain `d`. That is pre-existing and unrelated — the
     * point of this test is that whatever the AST holds survives the trip unchanged.
     */
    @Test
    fun `extraction rules round-trip`() {
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

        assertTrue(
            actual = rule is BuilderRule.Supported,
            message = "Extraction rules must be editable: " +
                (rule as? BuilderRule.Unsupported)?.reason.orEmpty(),
        )
        val generated = assertNotNull(
            actual = BuilderToRuleDsl.generate(state = BuilderEditorState.fromBuilderRule(rule = rule))
        )
        val reparsed = Parser(input = generated).parseRules().single()

        assertEquals(
            expected = ast.actions,
            actual = reparsed.actions,
            message = "The extraction and its \$1 argument must survive.\nGenerated:\n$generated",
        )
    }

    /** A pattern whose backslashes are doubled — the spelling the docs use — is byte-identical. */
    @Test
    fun `an escaped extraction pattern regenerates unchanged`() {
        val dsl = """
            rule "extract-digits" {
              when
                purpose contains "rent"
              then
                extract iban regex("DE(\\d+)", 1) tag $1
            }
        """.trimIndent()
        val rule = RuleAstToBuilderMapper.map(rule = Parser(input = dsl).parseRules().single())
        val generated = assertNotNull(
            actual = BuilderToRuleDsl.generate(state = BuilderEditorState.fromBuilderRule(rule = rule))
        )

        assertTrue(
            actual = generated.contains(other = """regex("DE(\\d+)", 1)"""),
            message = "the backslash must be re-escaped or the regex changes meaning: $generated",
        )
        assertTrue(
            actual = generated.contains(other = "tag \$1"),
            message = "the capture reference must stay unquoted: $generated",
        )
    }


    // ── the wider call forms ──────────────────────────────────────────────────

    @Test
    fun `a two-argument function round-trips`() {
        assertRoundTrips(condition = "daysBetween(registeredAt, reviewDate) >= 90")
    }

    @Test
    fun `a function wrapping a calculation of aggregates round-trips`() {
        assertRoundTrips(condition = "abs(sum(orders.total) - sum(refunds.amount)) > 1000")
    }

    @Test
    fun `a keyed join round-trips`() {
        assertRoundTrips(
            condition = """min(sumByKey("month", sales.amount, refunds.amount)) >= 0"""
        )
    }

    /** A bare predicate is desugared to `== true` by the parser; the Builder must not lose that. */
    @Test
    fun `a collection predicate round-trips`() {
        assertRoundTrips(condition = "every(orders[total > 0]) == true")
    }

    @Test
    fun `a negated collection predicate round-trips`() {
        assertRoundTrips(condition = """not any(orders[status == "failed"]) == true""")
    }

    @Test
    fun `a function call maps to the function operand, not to an aggregate`() {
        val ast = Parser(input = wrap(condition = "daysBetween(registeredAt, reviewDate) >= 90"))
            .parseRules().single()
        val rule = RuleAstToBuilderMapper.map(rule = ast) as BuilderRule.Supported
        val comparison = rule.conditionNodes.single() as BuilderConditionNode.Comparison
        val call = comparison.left as BuilderOperand.Call

        assertEquals(expected = "daysBetween", actual = call.function)
        assertEquals(expected = 2, actual = call.args.size)
    }

    @Test
    fun `a reduction over one path still maps to the aggregate operand`() {
        val ast = Parser(input = wrap(condition = "sum(orders.total) > 1")).parseRules().single()
        val rule = RuleAstToBuilderMapper.map(rule = ast) as BuilderRule.Supported
        val comparison = rule.conditionNodes.single() as BuilderConditionNode.Comparison

        assertTrue(
            actual = comparison.left is BuilderOperand.Aggregate,
            message = "existing rules must keep the aggregate panel, got: ${comparison.left}",
        )
    }

    // ── slices ────────────────────────────────────────────────────────────────

    @Test
    fun `a sliced projection round-trips`() {
        assertRoundTrips(condition = "sum(take(orders, 3).total) > 5000")
    }

    @Test
    fun `a slice filtered afterwards round-trips`() {
        assertRoundTrips(
            condition = """count(takeLast(orders, 10)[status == "failed"]) >= 3"""
        )
    }

    @Test
    fun `a slice applied after a filter round-trips`() {
        assertRoundTrips(
            condition = """sum(take(orders[status == "paid"], 3).total) > 100"""
        )
    }

    /**
     * The two orders mean different things, so the model has to keep them apart rather than
     * normalise one into the other.
     */
    @Test
    fun `slice order is preserved in both directions`() {
        val sliceFirst = roundTrip(condition = """count(take(orders, 3)[status == "paid"]) > 0""").second
        val filterFirst = roundTrip(condition = """count(take(orders[status == "paid"], 3)) > 0""").second

        assertTrue(actual = sliceFirst.contains(other = "take(orders, 3)["), message = sliceFirst)
        assertTrue(
            actual = filterFirst.contains(other = """take(orders[status == "paid"], 3)"""),
            message = filterFirst,
        )
    }

    // ── orderings ─────────────────────────────────────────────────────────────

    @Test
    fun `an ordering by a member round-trips`() {
        assertRoundTrips(condition = """sum(sortBy(orders, "total", desc).total) > 100""")
    }

    @Test
    fun `an ordering over a set of values round-trips`() {
        assertRoundTrips(condition = """sortBy(allowedStatuses, asc) contains "paid"""")
    }

    @Test
    fun `an ordering combined with a filter and a slice round-trips`() {
        assertRoundTrips(
            condition = """sum(take(sortBy(orders[status == "paid"], "total", desc), 3).total) > 100"""
        )
    }

    /**
     * Ordering then slicing gives the three largest; slicing then ordering gives an arbitrary three
     * put in order. The Builder has to keep them apart, or an edit silently changes what the rule
     * asks — the whole rule text is regenerated on every keystroke.
     */
    @Test
    fun `ordering order relative to a slice is preserved in both directions`() {
        val sortFirst = roundTrip(condition = """count(take(sortBy(orders, "total", desc), 3)) > 0""").second
        val sliceFirst = roundTrip(condition = """count(sortBy(take(orders, 3), "total", desc)) > 0""").second

        assertTrue(
            actual = sortFirst.contains(other = """take(sortBy(orders, "total", desc), 3)"""),
            message = sortFirst,
        )
        assertTrue(
            actual = sliceFirst.contains(other = """sortBy(take(orders, 3), "total", desc)"""),
            message = sliceFirst,
        )
    }

    /**
     * Editing the filters of a segment must not drop the ordering sitting beside them. The Builder
     * replaces the whole rule text, so a decoration the model loses is deleted from the file.
     */
    @Test
    fun `rewriting a segment's filters keeps its ordering`() {
        val original = Parser(
            input = wrap(condition = """count(sortBy(orders[status == "paid"], "total", desc)) > 0""")
        ).parseRules().single()
        val builderRule = RuleAstToBuilderMapper.map(rule = original) as BuilderRule.Supported
        val comparison = builderRule.conditionNodes.single() as BuilderConditionNode.Comparison
        val step = (comparison.left as BuilderOperand.Aggregate).path.single()

        val rewritten = step.withFilters(filters = step.filters)

        assertEquals(expected = step.sort, actual = rewritten.sort, message = "the ordering was dropped")
        assertEquals(expected = step.decorations, actual = rewritten.decorations)
    }

    // ── membership filters ────────────────────────────────────────────────────

    @Test
    fun `a membership filter against a named source round-trips`() {
        assertRoundTrips(
            condition = "sum(orders[status in allowedStatuses].total) > 100"
        )
    }

    @Test
    fun `a membership filter keeps its source unquoted`() {
        val (_, generated) = roundTrip(condition = "sum(orders[status in allowedStatuses].total) > 100")

        assertTrue(
            actual = generated.contains(other = "status in allowedStatuses"),
            message = "quoting the source would turn it into a text comparison: $generated",
        )
    }

    // ── drift guard ───────────────────────────────────────────────────────────

    @Test
    fun `the path functions are offered by the breadcrumb, not the call editor`() {
        assertEquals(
            expected = listOf("sortBy", "take", "takeLast").sorted(),
            actual = DslFunctions.pathFunctionNames().sorted(),
            message = "a call editor filtering on this list would start offering the new name",
        )
    }

    @Test
    fun `builder aggregate list matches the engine`() {
        assertEquals(
            expected = AggregateFunctionName.entries.filter { it.isAggregate }.map { it.dslName }.sorted(),
            actual = OperatorOptions.AGGREGATE_FUNCTIONS.sorted(),
            message = "OperatorOptions.AGGREGATE_FUNCTIONS drifted from AggregateFunctionName",
        )
    }
}
