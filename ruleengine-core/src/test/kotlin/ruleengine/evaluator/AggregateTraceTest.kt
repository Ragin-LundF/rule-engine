package ruleengine.evaluator

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.DecisionTree
import ruleengine.evaluator.trace.dto.NodeType
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conditions whose operand is an expression — an aggregate, arithmetic, or another field — compile to
 * [ruleengine.evaluator.compiled.ComparisonCompiledExpression] rather than to one of the dedicated
 * leaves. That class used to accept a trace collector and ignore it, so those conditions were absent
 * from the decision tree entirely and a rule built only from them showed a verdict with nothing
 * behind it.
 */
class AggregateTraceTest {

    private val schema = FieldSchema(
        name = "orders-v1",
        fields = mapOf(
            FieldId(value = "orders") to FieldDefinition(
                id = FieldId(value = "orders"),
                type = FieldType.STRING_SET
            ),
            FieldId(value = "budget") to FieldDefinition(
                id = FieldId(value = "budget"),
                type = FieldType.DECIMAL
            )
        )
    )

    private val orders = listOf(
        mapOf("amount" to 10, "status" to "paid"),
        mapOf("amount" to 20, "status" to "paid"),
        mapOf("amount" to 30, "status" to "open"),
        mapOf("amount" to 40, "status" to "open"),
        mapOf("amount" to 50, "status" to "cancelled")
    )

    @Test
    fun `an aggregate condition is recorded in the trace`() {
        val conditions = conditionsOf(condition = "count(orders) > 2")

        val node = conditions.single()
        assertEquals(expected = "count(orders)", actual = node.field)
        assertEquals(expected = "GT", actual = node.operator)
        assertEquals(expected = BigDecimal("2"), actual = node.expected)
        assertTrue(actual = node.result)
    }

    @Test
    fun `an aggregate condition records the value it actually found`() {
        val node = conditionsOf(condition = "sum(orders.amount) >= 1000").single()

        assertEquals(expected = false, actual = node.result)
        assertEquals(
            expected = BigDecimal("150"),
            actual = node.actual,
            message = "The trace must say what the sum really was, or a red row cannot explain itself",
        )
    }

    /**
     * The regression that would otherwise go unnoticed: a filter predicate runs once per element, so
     * instrumenting it would turn one condition into one node per row of the collection.
     * [ruleengine.evaluator.compiled.FieldAccessCompiledValueExpression] guards this by evaluating
     * filters with a null collector — this pins that guard.
     */
    @Test
    fun `a filter predicate adds one node for the whole comparison, not one per element`() {
        val conditions = conditionsOf(condition = """count(orders[status equals "paid"]) > 1""")

        assertEquals(
            expected = 1,
            actual = conditions.size,
            message = "Expected a single condition node over ${orders.size} elements, got $conditions",
        )
        assertEquals(expected = """count(orders[status equals "paid"])""", actual = conditions.single().field)
        assertEquals(expected = BigDecimal("2"), actual = conditions.single().actual)
    }

    @Test
    fun `an arithmetic operand is rendered into the condition label`() {
        val node = conditionsOf(condition = "sum(orders.amount) * 2 > 100").single()

        assertEquals(expected = "(sum(orders.amount) * 2)", actual = node.field)
        assertEquals(expected = BigDecimal("300"), actual = node.actual)
        assertTrue(actual = node.result)
    }

    /** A comparison against another field shows the concrete value it was measured against. */
    @Test
    fun `a field-to-field comparison records both sides`() {
        val node = conditionsOf(
            condition = "sum(orders.amount) > budget",
            extraFields = arrayOf("budget" to 200)
        ).single()

        assertEquals(expected = "sum(orders.amount)", actual = node.field)
        assertEquals(expected = BigDecimal("150"), actual = node.actual)
        assertEquals(expected = BigDecimal("200"), actual = node.expected)
        assertEquals(expected = false, actual = node.result)
    }

    /**
     * `actual` is opt-in per emitter, so the leaves that do not report one must leave it null — that
     * is what keeps it out of the serialized JSON for every node that has nothing to say.
     */
    @Test
    fun `a plain field condition still reports no actual value`() {
        val node = conditionsOf(condition = "budget >= 10", extraFields = arrayOf("budget" to 200)).single()

        assertEquals(expected = "budget", actual = node.field)
        assertNull(actual = node.actual)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun conditionsOf(
        condition: String,
        extraFields: Array<Pair<String, Any?>> = emptyArray(),
    ): List<DecisionNode> {
        val rule = """
            rule "aggregate-rule" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()

        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(
            ctx = RuleContext.of("orders" to orders, *extraFields),
            schema = schema
        )
        val result = RuleEngine(compiledRules = compiled).evaluate(prepared = prepared, includeTrace = true)

        val tree = result.trace as? DecisionTree
        assertNotNull(actual = tree)
        val root = tree.root
        assertNotNull(actual = root)
        return collectConditions(node = root)
    }

    private fun collectConditions(node: DecisionNode): List<DecisionNode> {
        if (node.type == NodeType.CONDITION) {
            return listOf(node)
        }
        return node.children.flatMap { child -> collectConditions(node = child) }
    }
}
