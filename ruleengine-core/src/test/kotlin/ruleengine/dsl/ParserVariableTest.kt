package ruleengine.dsl

import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.VariableRefAst
import ruleengine.dsl.ast.VariableRefLiteral
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.TokenType
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParserVariableTest {

    @Test
    fun `lexer reads a variable reference as one identifier`() {
        val tokens = Lexer(input = "\$orderTotal").tokenize()

        assertEquals(expected = TokenType.IDENT, actual = tokens[0].type)
        assertEquals(expected = "\$orderTotal", actual = tokens[0].text)
    }

    @Test
    fun `lexer still reads an extraction reference as one identifier`() {
        val tokens = Lexer(input = "\$1").tokenize()

        assertEquals(expected = TokenType.IDENT, actual = tokens[0].type)
        assertEquals(expected = "\$1", actual = tokens[0].text)
    }

    @Test
    fun `set clause becomes an assignment on the rule`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  set orderTotal = sum(orders[amount > 0].amount)
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = rule.assignments.size)
        assertEquals(expected = "orderTotal", actual = rule.assignments[0].name)
        assertIs<FunctionCallValueAst>(value = rule.assignments[0].expression)
        assertTrue(actual = rule.actions.isEmpty())
    }

    @Test
    fun `a set clause and an action can share one then block`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  set fee = amount * 2
                  label "priced"
            """.trimIndent()
        )

        assertEquals(expected = listOf("fee"), actual = rule.assignments.map { it.name })
        assertIs<ArithmeticValueAst>(value = rule.assignments[0].expression)
        assertEquals(expected = listOf("label"), actual = rule.actions.map { it.name })
    }

    @Test
    fun `a variable read in a condition becomes a comparison operand`() {
        val rule = parseSingle(
            body = """
                when
                  ${'$'}orderTotal >= 1000
                then
                  label "vip"
            """.trimIndent()
        )

        val condition = assertIs<ComparisonExpressionAst>(value = rule.condition)
        assertEquals(expected = VariableRefAst(name = "orderTotal"), actual = condition.left)
    }

    @Test
    fun `a variable reads as an action argument`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  score ${'$'}riskScore
            """.trimIndent()
        )

        assertEquals(
            expected = listOf(VariableRefLiteral(name = "riskScore")),
            actual = rule.actions[0].arguments
        )
    }

    @Test
    fun `an all digit reference stays an extraction reference`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  extract purpose regex("(\\d+)", 1) label ${'$'}1
            """.trimIndent()
        )

        assertEquals(expected = "ExtractionRefLiteral", actual = rule.actions[0].arguments[0]::class.simpleName)
    }

    @Test
    fun `a list argument after a set clause is not read as a filter`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  set total = amount
                  tags ["a", "b"]
            """.trimIndent()
        )

        assertEquals(expected = listOf("total"), actual = rule.assignments.map { it.name })
        assertEquals(expected = listOf("tags"), actual = rule.actions.map { it.name })
        assertEquals(expected = "ListLiteral", actual = rule.actions[0].arguments[0]::class.simpleName)
    }

    @Test
    fun `set without an equals sign is rejected`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 0
                    then
                      set total amount
                """.trimIndent()
            )
        }

        assertTrue(
            actual = failure.messageText.contains(other = "Expected '='"),
            message = "unexpected message: ${failure.messageText}"
        )
    }

    @Test
    fun `set with a dollar prefixed name is rejected`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 0
                    then
                      set ${'$'}total = amount
                """.trimIndent()
            )
        }

        assertTrue(
            actual = failure.messageText.contains(other = "without the '\$' prefix"),
            message = "unexpected message: ${failure.messageText}"
        )
    }

    private fun parseSingle(body: String) = Parser(
        input = """
            rule "r" {
              description "d"
              $body
            }
        """.trimIndent()
    ).parseRules().single()
}
