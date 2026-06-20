package ui.builder

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `simple condition maps to Supported with one condition`() {
        val ast = rule(condition = cond("purpose", "contains", "rent"))
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(1, result.conditions.size)
        assertEquals("purpose", result.conditions[0].field)
        assertEquals("contains", result.conditions[0].operator)
        assertEquals("\"rent\"", result.conditions[0].value)
        assertEquals(ConditionJoin.SINGLE, result.conditionJoin)
    }

    @Test
    fun `two-condition AND rule maps to Supported with AND join`() {
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
        assertEquals(2, result.conditions.size)
        assertEquals(ConditionJoin.AND, result.conditionJoin)
        assertEquals("purpose", result.conditions[0].field)
        assertEquals("contains", result.conditions[0].operator)
        assertEquals("amount", result.conditions[1].field)
        assertEquals(">=", result.conditions[1].operator)
        assertEquals("500", result.conditions[1].value)
    }

    @Test
    fun `static string action maps correctly`() {
        val ast = rule(
            condition = cond("purpose", "contains", "rent"),
            actions = listOf(ActionAst(name = "label", arguments = listOf(StringLiteral("rent")))),
        )
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals(1, result.actions.size)
        assertEquals("label", result.actions[0].name)
        assertEquals(listOf("\"rent\""), result.actions[0].arguments)
    }

    @Test
    fun `OrAst produces Unsupported`() {
        val ast = rule(
            condition = OrAst(
                children = listOf(
                    cond("purpose", "contains", "rent"),
                    numCond("amount", ">=", "500"),
                )
            )
        )
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Unsupported>(result)
        assertTrue(result.reason.isNotBlank())
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
        assertEquals(listOf("\"food\"", "\"rent\""), result.conditions[0].listItems)
    }

    @Test
    fun `rule id is preserved in Supported result`() {
        val ast = rule(id = "my-rule", condition = cond("x", "equals", "y"))
        val result = RuleAstToBuilderMapper.map(ast)

        assertIs<BuilderRule.Supported>(result)
        assertEquals("my-rule", result.id)
    }
}
