package ruleengine.compiler

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.SortSegmentAst
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

/** `sortBy(path, asc|desc)` and `sortBy(path, "member", asc|desc)`. */
class CollectionSortTest {

    private val schema = FieldSchema(
        name = "sort-schema",
        fields = mapOf(
            FieldId(value = "orders") to FieldDefinition(
                id = FieldId(value = "orders"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "total") to FieldDefinition(
                        id = FieldId(value = "total"),
                        type = FieldType.DECIMAL
                    ),
                    FieldId(value = "label") to FieldDefinition(
                        id = FieldId(value = "label"),
                        type = FieldType.TEXT
                    ),
                    FieldId(value = "placedOn") to FieldDefinition(
                        id = FieldId(value = "placedOn"),
                        type = FieldType.DATE
                    ),
                    FieldId(value = "lines") to FieldDefinition(
                        id = FieldId(value = "lines"),
                        type = FieldType.COLLECTION
                    )
                )
            ),
            FieldId(value = "tags") to FieldDefinition(
                id = FieldId(value = "tags"),
                type = FieldType.STRING_SET
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

    // --- ordering values ---

    @Test
    fun `descending orders the largest first`() {
        assertTrue(
            actual = evaluate(
                condition = "sum(take(sortBy(orders, \"total\", desc), 3).total) == 1200",
                "orders" to orders(100, 500, 200, 400, 300)
            ),
            message = "the three largest totals are 500, 400 and 300"
        )
        assertFalse(
            actual = evaluate(
                condition = "sum(take(sortBy(orders, \"total\", desc), 3).total) == 800",
                "orders" to orders(100, 500, 200, 400, 300)
            ),
            message = "800 is the first three in source order, i.e. the ordering being ignored"
        )
    }

    @Test
    fun `ascending orders the smallest first`() {
        assertTrue(
            actual = evaluate(
                condition = "sum(take(sortBy(orders, \"total\", asc), 2).total) == 300",
                "orders" to orders(100, 500, 200, 400, 300)
            ),
            message = "the two smallest totals are 100 and 200"
        )
        assertFalse(
            actual = evaluate(
                condition = "sum(take(sortBy(orders, \"total\", asc), 2).total) == 600",
                "orders" to orders(100, 500, 200, 400, 300)
            )
        )
    }

    @Test
    fun `text members order lexicographically`() {
        val rows = listOf(
            mapOf("label" to "pear"),
            mapOf("label" to "apple"),
            mapOf("label" to "fig")
        )
        assertEquals(
            expected = listOf("apple", "fig", "pear"),
            actual = variable(expression = "sortBy(orders, \"label\", asc).label", "orders" to rows)
        )
    }

    @Test
    fun `date members order chronologically, not as declared text`() {
        val rows = listOf(
            mapOf("placedOn" to "2024-07-01"),
            mapOf("placedOn" to "2024-01-31"),
            mapOf("placedOn" to "2024-03-15")
        )
        assertEquals(
            expected = listOf("2024-07-01", "2024-03-15", "2024-01-31"),
            actual = variable(expression = "sortBy(orders, \"placedOn\", desc).placedOn", "orders" to rows)
        )
    }

    @Test
    fun `a set of values orders by the values themselves`() {
        assertEquals(
            expected = listOf("apple", "fig", "pear"),
            actual = variable(expression = "sortBy(tags, asc)", "tags" to listOf("pear", "apple", "fig"))
        )
        assertEquals(
            expected = listOf("pear", "fig", "apple"),
            actual = variable(expression = "sortBy(tags, desc)", "tags" to listOf("pear", "apple", "fig"))
        )
    }

    @Test
    fun `an empty collection orders to nothing`() {
        assertTrue(
            actual = evaluate(condition = "count(sortBy(orders, \"total\", desc)) == 0", "orders" to emptyList<Any>())
        )
    }

    // --- missing and mixed keys ---

    @Test
    fun `elements with no key for the member sort last in both directions`() {
        val rows = listOf(
            mapOf("total" to 100, "label" to "has-none"),
            mapOf("label" to "absent"),
            mapOf("total" to 900, "label" to "biggest")
        )
        assertEquals(
            expected = listOf("biggest", "has-none", "absent"),
            actual = variable(expression = "sortBy(orders, \"total\", desc).label", "orders" to rows),
            message = "descending must not promote the row that carries no total"
        )
        assertEquals(
            expected = listOf("has-none", "biggest", "absent"),
            actual = variable(expression = "sortBy(orders, \"total\", asc).label", "orders" to rows)
        )
    }

    @Test
    fun `equal keys keep their source order`() {
        val rows = listOf(
            mapOf("total" to 5, "label" to "first"),
            mapOf("total" to 5, "label" to "second"),
            mapOf("total" to 5, "label" to "third")
        )
        assertEquals(
            expected = listOf("first", "second", "third"),
            actual = variable(expression = "sortBy(orders, \"total\", asc).label", "orders" to rows),
            message = "a stable sort is what makes take() after it deterministic"
        )
    }

    @Test
    fun `a set of text orders lexicographically, digits included`() {
        assertEquals(
            expected = listOf("10", "2", "beta"),
            actual = variable(expression = "sortBy(tags, asc)", "tags" to listOf("beta", "10", "2")),
            message = "a string_set holds text, and \"10\" precedes \"2\" as text"
        )
    }

    @Test
    fun `values of different kinds group by kind rather than ordering arbitrarily`() {
        val rows = listOf(
            mapOf("total" to "later", "label" to "text"),
            mapOf("total" to 10, "label" to "ten"),
            mapOf("total" to 2, "label" to "two")
        )
        assertEquals(
            expected = listOf("two", "ten", "text"),
            actual = variable(expression = "sortBy(orders, \"total\", asc).label", "orders" to rows),
            message = "numbers order numerically and as a group before text"
        )
    }

    // --- composition ---

    @Test
    fun `a filter written before the ordering narrows what is ordered`() {
        val rows = listOf(
            mapOf("total" to 900, "label" to "skip"),
            mapOf("total" to 100, "label" to "keep"),
            mapOf("total" to 300, "label" to "keep")
        )
        assertEquals(
            expected = listOf("300", "100"),
            actual = variable(
                expression = "sortBy(orders[label == \"keep\"], \"total\", desc).total",
                "orders" to rows
            ),
            message = "the 900 was filtered out before the ordering ever saw it"
        )
    }

    @Test
    fun `ordering before slicing differs from slicing before ordering`() {
        val rows = orders(100, 500, 200)
        assertEquals(
            expected = listOf("500", "200"),
            actual = variable(
                expression = "take(sortBy(orders, \"total\", desc), 2).total",
                "orders" to rows
            ),
            message = "ordered first: the two largest"
        )
        assertEquals(
            expected = listOf("500", "100"),
            actual = variable(
                expression = "sortBy(take(orders, 2), \"total\", desc).total",
                "orders" to rows
            ),
            message = "sliced first: the first two, put in order"
        )
    }

    // --- round trip ---

    @Test
    fun `the parsed path carries a sort segment`() {
        val asts = Parser(input = rule(condition = "count(sortBy(orders, \"total\", desc)) > 0")).parseRules()
        val argument = ((asts[0].condition as ComparisonExpressionAst).left as ruleengine.dsl.ast.FunctionCallValueAst)
            .arguments.single() as FieldAccessAst
        assertEquals(
            expected = listOf(SortSegmentAst(member = "total", descending = true)),
            actual = argument.path.filterIsInstance<SortSegmentAst>()
        )
    }

    @Test
    fun `rendering reproduces what was written`() {
        assertEquals(
            expected = "sum(take(sortBy(orders, \"total\", desc), 3).total)",
            actual = rendered(condition = "sum(take(sortBy(orders, \"total\", desc), 3).total) > 0")
        )
        assertEquals(
            expected = "count(sortBy(tags, asc))",
            actual = rendered(condition = "count(sortBy(tags, asc)) > 0")
        )
    }

    // --- validation ---

    @Test
    fun `a valid ordering reports nothing`() {
        val diagnostics = validate(condition = "count(sortBy(orders, \"total\", desc)) > 0")
        assertTrue(actual = diagnostics.isEmpty(), message = "diagnostics: $diagnostics")
    }

    @Test
    fun `ordering something that is not a collection is rejected`() {
        val diagnostics = validate(condition = "count(sortBy(customer, \"name\", asc)) > 0")
        assertEquals(expected = listOf(Severity.ERROR), actual = diagnostics.map { it.severity })
        assertTrue(
            actual = diagnostics.single().message.contains(other = "expects a collection or a set of values"),
            message = diagnostics.single().message
        )
    }

    @Test
    fun `a member name on a set of values is rejected`() {
        val diagnostics = validate(condition = "count(sortBy(tags, \"total\", asc)) > 0")
        assertEquals(expected = listOf(Severity.ERROR), actual = diagnostics.map { it.severity })
        assertTrue(
            actual = diagnostics.single().message.contains(other = "take no member name"),
            message = diagnostics.single().message
        )
    }

    @Test
    fun `omitting the member on a collection of structures is rejected`() {
        val diagnostics = validate(condition = "count(sortBy(orders, asc)) > 0")
        assertEquals(expected = listOf(Severity.ERROR), actual = diagnostics.map { it.severity })
        assertTrue(
            actual = diagnostics.single().message.contains(other = "needs the member to order by"),
            message = diagnostics.single().message
        )
    }

    @Test
    fun `an undeclared member is rejected with a suggestion`() {
        val diagnostics = validate(condition = "count(sortBy(orders, \"totl\", desc)) > 0")
        assertEquals(expected = listOf(Severity.ERROR), actual = diagnostics.map { it.severity })
        assertEquals(expected = "total", actual = diagnostics.single().suggestion)
    }

    @Test
    fun `ordering by a member that is itself a collection is rejected`() {
        val diagnostics = validate(condition = "count(sortBy(orders, \"lines\", asc)) > 0")
        assertEquals(expected = listOf(Severity.ERROR), actual = diagnostics.map { it.severity })
        assertTrue(
            actual = diagnostics.single().message.contains(other = "has no order of its own"),
            message = diagnostics.single().message
        )
    }

    @Test
    fun `a direction that is neither asc nor desc is a parse error`() {
        assertFailsWith<ParseException> {
            Parser(input = rule(condition = "count(sortBy(orders, \"total\", upwards)) > 0")).parseRules()
        }
    }

    @Test
    fun `an unquoted member is a parse error`() {
        assertFailsWith<ParseException> {
            Parser(input = rule(condition = "count(sortBy(orders, total, desc)) > 0")).parseRules()
        }
    }

    /**
     * A condition falls back to the legacy named-operator path when the modern parse throws, which
     * replaces the message with that path's own — the same thing happens to `take`. An assignment
     * has no such fallback, so it is where the wording can actually be checked.
     */
    @Test
    fun `the parse error names asc, desc and the quoting rule`() {
        val failure = assertFailsWith<ParseException> {
            Parser(
                input = """
                    rule "r" {
                      description "d"
                      when
                        count(orders) > 0
                      then
                        set ordered = sortBy(orders, total, desc)
                    }
                """.trimIndent()
            ).parseRules()
        }
        val message = failure.message.orEmpty()
        assertTrue(actual = message.contains(other = "expects asc or desc"), message = message)
        assertTrue(actual = message.contains(other = "must be quoted"), message = message)
    }

    @Test
    fun `an ordered path compares through the value path rather than as a field name`() {
        val diagnostics = validate(condition = "sortBy(tags, asc) contains \"beta\"")
        assertTrue(
            actual = diagnostics.none { diagnostic -> diagnostic.severity == Severity.ERROR },
            message = "diagnostics: $diagnostics"
        )
        assertTrue(
            actual = evaluate(condition = "sortBy(tags, asc) contains \"beta\"", "tags" to listOf("beta", "alpha"))
        )
    }

    // --- helpers ---

    private fun evaluate(condition: String, vararg fields: Pair<String, Any?>): Boolean {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }

    /**
     * The value an ordered expression publishes, rendered as text.
     *
     * Text rather than the values themselves: a number leaves the engine as a `BigDecimal`, and an
     * assertion written against `listOf(300, 100)` would compare `Int` against `BigDecimal` and fail
     * for a reason that has nothing to do with the ordering under test.
     */
    private fun variable(expression: String, vararg fields: Pair<String, Any?>): List<String> {
        val text = """
            rule "sort-output" {
              description "publishes the ordered collection"
              when
                count(orders) >= 0
              then
                set ordered = $expression
            }
        """.trimIndent()
        val asts = Parser(input = text).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        val published = RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).variables["ordered"]
        return (published as List<*>).map { value -> value.toString() }
    }

    private fun rendered(condition: String): String {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        return ValueExpressionRenderer.render(expr = (asts[0].condition as ComparisonExpressionAst).left)
    }

    private fun validate(condition: String) = Validator.validate(
        asts = Parser(input = rule(condition = condition)).parseRules(),
        schema = schema
    ).diagnostics

    private fun rule(condition: String): String = """
        rule "sort-test" {
          description "orders a collection"
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
