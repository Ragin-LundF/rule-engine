package ui.builder

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.parser.Parser
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderRule
import ui.builder.model.fieldOperand
import ui.builder.model.filters
import ui.builder.model.names
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RuleAstToBuilderMapperTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun rule(
        id: String = "test-rule",
        condition: ruleengine.dsl.ast.ExpressionAst,
        actions: List<ActionAst> = emptyList(),
    ) = RuleAst(id = id, condition = condition, actions = actions)

    private fun cond(field: String, op: String, value: String) =
        ConditionAst(field = field, operator = op, value = StringLiteral(value))

    private fun numCond(field: String, op: String, value: String) =
        ConditionAst(field = field, operator = op, value = NumberLiteral(value))

    /** Unwrap a node as a leaf [BuilderConditionNode.Condition]. */
    private fun leaf(node: BuilderConditionNode): BuilderConditionNode.Condition =
        node as BuilderConditionNode.Condition

    /** Unwrap a node as a [BuilderConditionNode.Group]. */
    private fun group(node: BuilderConditionNode): BuilderConditionNode.Group =
        node as BuilderConditionNode.Group

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `simple condition maps to Supported with one condition`() {
        val ast = rule(condition = cond("purpose", "contains", "rent"))
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(expected = 1, actual = result.conditionNodes.size)

        val c = leaf(result.conditionNodes[0])
        assertEquals(expected = "purpose", actual = c.field)
        assertEquals(expected = "contains", actual = c.operator)
        assertEquals(expected = "rent", actual = c.value)
        assertEquals(expected = "", actual = c.joinToPrevious)
    }

    @Test
    fun `two-condition AND rule maps to Supported with per-link joins`() {
        val ast = rule(
            condition = AndAst(
                children = listOf(
                    cond("purpose", "contains", "rent"),
                    numCond("amount", ">=", "500"),
                )
            )
        )
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(expected = 2, actual = result.conditionNodes.size)

        val c0 = leaf(result.conditionNodes[0])
        assertEquals(expected = "purpose", actual = c0.field)
        assertEquals(expected = "contains", actual = c0.operator)
        assertEquals(expected = "", actual = c0.joinToPrevious)

        val c1 = leaf(result.conditionNodes[1])
        assertEquals(expected = "amount", actual = c1.field)
        assertEquals(expected = ">=", actual = c1.operator)
        assertEquals(expected = "500", actual = c1.value)
        assertEquals(expected = "and", actual = c1.joinToPrevious)
    }

    @Test
    fun `static string action maps correctly`() {
        val ast = rule(
            condition = cond("purpose", "contains", "rent"),
            actions = listOf(ActionAst(name = "label", arguments = listOf(StringLiteral("rent")))),
        )
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(expected = 1, actual = result.actions.size)
        assertEquals(expected = "label", actual = result.actions[0].name)
        assertEquals(expected = listOf("rent"), actual = result.actions[0].arguments)
    }

    @Test
    fun `OrAst maps to supported builder rule with per-link joins`() {
        val ast = rule(
            condition = OrAst(
                children = listOf(
                    cond("purpose", "contains", "rent"),
                    numCond("amount", ">=", "500"),
                )
            )
        )
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(expected = "", actual = leaf(result.conditionNodes[0]).joinToPrevious)
        assertEquals(expected = "or", actual = leaf(result.conditionNodes[1]).joinToPrevious)
    }

    @Test
    fun `mixed AND OR preserves groups`() {
        // OrAst(AndAst(A, B), C) -> Group(A,B) with inner join "and", outer join "or"
        val ast = rule(
            condition = OrAst(
                children = listOf(
                    AndAst(
                        children = listOf(
                            cond("purpose", "contains", "rent"),
                            numCond("amount", ">=", "500"),
                        )
                    ),
                    cond("category", "equals", "housing"),
                )
            )
        )
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        // Top-level: 1 Group + 1 leaf
        assertEquals(expected = 2, actual = result.conditionNodes.size)

        val g = group(result.conditionNodes[0])
        assertEquals(expected = "", actual = g.joinToPrevious)
        assertEquals(expected = 2, actual = g.nodes.size)
        assertEquals(expected = "", actual = leaf(g.nodes[0]).joinToPrevious)
        assertEquals(expected = "and", actual = leaf(g.nodes[1]).joinToPrevious)

        val c = leaf(result.conditionNodes[1])
        assertEquals(expected = "or", actual = c.joinToPrevious)
    }

    @Test
    fun `ListLiteral in condition maps to list items`() {
        val ast = rule(
            condition = ConditionAst(
                field = "category",
                operator = "in",
                value = ListLiteral(items = listOf(StringLiteral("food"), StringLiteral("rent"))),
            )
        )
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(expected = listOf("food", "rent"), actual = leaf(result.conditionNodes[0]).listItems)
    }

    @Test
    fun `rule id is preserved in Supported result`() {
        val ast = rule(id = "my-rule", condition = cond("x", "equals", "y"))
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(expected = "my-rule", actual = result.id)
    }

    /**
     * A filter holds two operands, so one may name a path that filters again on the way — the inner
     * `[...]` lands in that operand's own path steps rather than having nowhere to go.
     *
     * This used to lock the rule, back when `BuilderFilter` was a flat `field op value` row and the
     * inner brackets would have been dropped silently on the way back to DSL.
     */
    @Test
    fun `filter containing a nested filter maps`() {
        val ast = parseRule(condition = """count(orders[items[price > 0].sku == "x"]) > 0""")
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        val comparison = result.conditionNodes.single() as BuilderConditionNode.Comparison
        val outerFilter = (comparison.left as BuilderOperand.Aggregate).path.single().filters.single()
        val innerPath = (outerFilter.left as BuilderOperand.FieldRef).path

        assertEquals(expected = listOf("items", "sku"), actual = innerPath.names)
        assertEquals(
            expected = fieldOperand(name = "price"),
            actual = innerPath.first().filters.single().left,
            message = "The inner filter belongs to the segment it filters",
        )
    }

    private fun parseRule(condition: String): RuleAst = Parser(
        input = """
            rule "filtered" {
              when
                $condition
              then
                flag "hit"
            }
        """.trimIndent()
    ).parseRules().single()
}
