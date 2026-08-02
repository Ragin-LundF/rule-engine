package ruleengine.compiler

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.SliceSegmentAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `take(path, n)` and `takeLast(path, n)` — REQ-02. */
class CollectionSliceTest {

    private val schema = FieldSchema(
        name = "slice-schema",
        fields = mapOf(
            FieldId(value = "orders") to FieldDefinition(
                id = FieldId(value = "orders"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "total") to FieldDefinition(
                        id = FieldId(value = "total"),
                        type = FieldType.DECIMAL
                    ),
                    FieldId(value = "successful") to FieldDefinition(
                        id = FieldId(value = "successful"),
                        type = FieldType.BOOLEAN
                    )
                )
            ),
            FieldId(value = "customer") to FieldDefinition(
                id = FieldId(value = "customer"),
                type = FieldType.OBJECT,
                fields = mapOf(
                    FieldId(value = "name") to FieldDefinition(
                        id = FieldId(value = "name"),
                        type = FieldType.TEXT
                    )
                )
            )
        )
    )

    private fun orders(vararg totals: Int): List<Map<String, Any?>> {
        return totals.map { total -> mapOf("total" to total) }
    }

    // --- take ---

    @Test
    fun `take selects a prefix in source order`() {
        assertTrue(
            actual = evaluate(
                condition = "sum(take(orders, 3).total) == 600",
                "orders" to orders(100, 200, 300, 400, 500)
            ),
            message = "only the first three orders may be summed"
        )
    }

    @Test
    fun `take does not include elements past the bound`() {
        assertFalse(
            actual = evaluate(
                condition = "sum(take(orders, 3).total) == 1500",
                "orders" to orders(100, 200, 300, 400, 500)
            ),
            message = "summing all five would mean the slice was ignored"
        )
    }

    @Test
    fun `take on a shorter collection returns what is available`() {
        assertTrue(
            actual = evaluate(condition = "sum(take(orders, 10).total) == 300", "orders" to orders(100, 200))
        )
    }

    @Test
    fun `take on an empty collection yields nothing`() {
        assertTrue(
            actual = evaluate(condition = "count(take(orders, 3)) == 0", "orders" to emptyList<Any>())
        )
    }

    @Test
    fun `take of zero elements yields nothing`() {
        assertTrue(
            actual = evaluate(condition = "count(take(orders, 0)) == 0", "orders" to orders(100, 200))
        )
    }

    // --- takeLast ---

    @Test
    fun `takeLast selects a suffix in source order`() {
        assertTrue(
            actual = evaluate(
                condition = "sum(takeLast(orders, 2).total) == 900",
                "orders" to orders(100, 200, 300, 400, 500)
            ),
            message = "the last two orders are 400 and 500"
        )
    }

    /**
     * The documented REQ-02 example. Slicing happens first, so this counts failures *among the last
     * ten events* — not the last ten failures.
     */
    @Test
    fun `takeLast composes with a filter applied after the slice`() {
        val events = (1..12).map { index -> mapOf("successful" to (index <= 10)) }

        assertTrue(
            actual = evaluate(
                condition = "count(takeLast(orders, 10)[successful == false]) >= 2",
                "orders" to events
            ),
            message = "the last ten events hold both failures"
        )
        assertFalse(
            actual = evaluate(
                condition = "count(takeLast(orders, 10)[successful == false]) >= 3",
                "orders" to events
            )
        )
    }

    @Test
    fun `a filter before the slice narrows first`() {
        val mixed = listOf(
            mapOf("total" to 100, "successful" to true),
            mapOf("total" to 200, "successful" to false),
            mapOf("total" to 300, "successful" to true)
        )

        assertTrue(
            actual = evaluate(
                condition = """sum(take(orders[successful == true], 1).total) == 100""",
                "orders" to mixed
            ),
            message = "filtering first leaves 100 and 300, of which the prefix is 100"
        )
    }

    @Test
    fun `a slice is usable in arithmetic`() {
        assertTrue(
            actual = evaluate(
                condition = "sum(take(orders, 2).total) * 2 == 600",
                "orders" to orders(100, 200, 300)
            )
        )
    }

    // --- parsing and rendering ---

    @Test
    fun `a slice parses into a path segment rather than a function call`() {
        val condition = parseCondition(condition = "sum(take(orders, 3).total) > 1")
        val argument = ((condition.left as FunctionCallValueAst).arguments.single() as FieldAccessAst)

        assertEquals(
            expected = listOf(false to "3"),
            actual = argument.path.filterIsInstance<SliceSegmentAst>().map { it.fromEnd to it.count }
        )
        assertEquals(
            expected = 3,
            actual = argument.path.size,
            message = "orders, the slice, then total"
        )
    }

    @Test
    fun `takeLast records that it counts from the end`() {
        val condition = parseCondition(condition = "count(takeLast(orders, 5)) > 1")
        val argument = ((condition.left as FunctionCallValueAst).arguments.single() as FieldAccessAst)

        assertEquals(
            expected = listOf(true to "5"),
            actual = argument.path.filterIsInstance<SliceSegmentAst>().map { it.fromEnd to it.count }
        )
    }

    @Test
    fun `a slice renders back to the spelling it was written in`() {
        val condition = parseCondition(condition = "sum(takeLast(orders, 2).total) > 1")

        assertEquals(
            expected = "sum(takeLast(orders, 2).total)",
            actual = ValueExpressionRenderer.render(expr = condition.left)
        )
    }

    @Test
    fun `a slice with a filter after it renders back unchanged`() {
        val condition = parseCondition(condition = "count(takeLast(orders, 10)[successful == false]) > 1")

        assertEquals(
            expected = "count(takeLast(orders, 10)[successful == false])",
            actual = ValueExpressionRenderer.render(expr = condition.left)
        )
    }

    @Test
    fun `a non-numeric slice size is rejected while parsing`() {
        assertFailsWith<ParseException> {
            Parser(input = rule(condition = """sum(take(orders, "three").total) > 1""")).parseRules()
        }
    }

    @Test
    fun `slicing something other than a path is rejected while parsing`() {
        assertFailsWith<ParseException> {
            Parser(input = rule(condition = "sum(take(sum(orders.total), 3)) > 1")).parseRules()
        }
    }

    // --- validation ---

    @Test
    fun `a negative slice size is rejected at validation time`() {
        val error = validate(condition = "sum(take(orders, -1).total) > 1")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
        assertTrue(
            actual = error?.message?.contains(other = "non-negative") == true,
            message = "the diagnostic must say what is wrong with the size, got: ${error?.message}"
        )
    }

    @Test
    fun `slicing a non-collection is rejected at validation time`() {
        val error = validate(condition = "count(take(customer, 2)) > 1")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertTrue(
            actual = error?.message?.contains(other = "expects a collection") == true,
            message = "the diagnostic must name the problem, got: ${error?.message}"
        )
    }

    @Test
    fun `a well-formed slice validates without errors`() {
        val errors = validate(condition = "sum(take(orders, 3).total) > 1")
            .filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertTrue(actual = errors.isEmpty(), message = "expected no errors, got: $errors")
    }

    private fun parseCondition(condition: String): ComparisonExpressionAst {
        val rules = Parser(input = rule(condition = condition)).parseRules()
        return rules.single().condition as ComparisonExpressionAst
    }

    private fun evaluate(condition: String, vararg fields: Pair<String, Any?>): Boolean {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }

    private fun validate(condition: String) = Validator.validate(
        asts = Parser(input = rule(condition = condition)).parseRules(),
        schema = schema
    ).diagnostics

    private fun rule(condition: String): String = """
        rule "slice-test" {
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
